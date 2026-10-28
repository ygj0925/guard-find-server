/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.guardfind.server.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.guardfind.server.ai.config.AiChatProperties;
import com.guardfind.server.ai.model.resp.AiTextResp;
import com.guardfind.server.ai.service.AiInsightService;
import com.guardfind.server.ai.support.AiStatsTextBuilder;
import com.guardfind.server.ai.support.DecomposeParser;
import com.guardfind.server.ai.support.LlmClient;
import com.guardfind.server.ai.support.LlmMessage;
import com.guardfind.server.ledger.model.resp.LedgerSummaryResp;
import com.guardfind.server.ledger.service.LedgerService;
import com.guardfind.server.tool.model.resp.ToolHabitResp;
import com.guardfind.server.tool.service.ToolHabitService;
import com.guardfind.server.tool.service.ToolTodoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.continew.starter.core.exception.BusinessException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * AI 洞察实现。
 * <p>
 * 数字一律由服务端统计好拼进提示词,模型只负责"写成话",防止编造;
 * 未配置 Key 时降级为事实底稿直出(带测试模式标注),功能可验证。
 * </p>
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiInsightServiceImpl implements AiInsightService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final LlmClient llmClient;
    private final AiChatProperties properties;
    private final LedgerService ledgerService;
    private final ToolTodoService todoService;
    private final ToolHabitService habitService;

    @Override
    public AiTextResp weeklyReport(String date) {
        LocalDate anchor = parseDate(date);
        String periodLabel = anchor.with(java.time.DayOfWeek.MONDAY).format(ISO) + " 那一周";
        String ledgerFacts = AiStatsTextBuilder.ledgerFacts(ledgerService.summary("week", anchor
            .format(ISO), null), periodLabel);
        int[] todos = todoStats();
        String[] habits = habitStats();
        String facts = ledgerFacts + "\n" + AiStatsTextBuilder.lifeFacts(todos[0], todos[1], Integer
            .parseInt(habits[0]), Integer.parseInt(habits[1]), Integer.parseInt(habits[2]), habits[3]);
        return generate("你是用户的个人助理。根据下面这份本周真实数据,写一段 150 字以内、" + "温暖不啰嗦的周报:先肯定亮点,再给一条下周的小建议。不要编造数据,数字照抄。\n\n" + facts, facts);
    }

    @Override
    public AiTextResp decompose(String content) {
        String trimmed = StrUtil.trim(content);
        if (StrUtil.isBlank(trimmed) || trimmed.length() > 200) {
            throw new BusinessException("请输入 200 字以内的目标");
        }
        boolean mockMode = StrUtil.isBlank(properties.getApiKey());
        if (mockMode) {
            return text(List.of("第一步:明确「" + StrUtil
                .maxLength(trimmed, 40) + "」的具体产出", "第二步:列出所需资源", "第三步:先做 15 分钟"), true);
        }
        String output = llmClient.chat(List.of(LlmMessage
            .system("你是一个任务拆解助手。把用户的目标拆成 3-5 个可直接执行的短步骤," + "只输出 JSON 字符串数组,如 [\"步骤1\",\"步骤2\"],不要输出其他内容。"), LlmMessage
                .user(trimmed)));
        List<String> items = DecomposeParser.parse(output);
        if (items == null) {
            log.warn("[ai] decompose 输出无法解析: {}", StrUtil.maxLength(output, 300));
            throw new BusinessException("AI 返回格式异常,请重试");
        }
        return text(items, false);
    }

    @Override
    public AiTextResp ledgerInsight(String period, String date) {
        String normalized = "week".equals(period) || "month".equals(period) ? period : "month";
        LocalDate anchor = parseDate(date);
        String periodLabel = "week".equals(normalized) ? "本周" : "本月";
        LedgerSummaryResp summary = ledgerService.summary(normalized, anchor.format(ISO), null);
        String facts = AiStatsTextBuilder.ledgerFacts(summary, periodLabel);
        return generate("你是记账分析师。根据下面的真实数据写 100 字以内点评:" + "点出最大的开销方向,给一条具体的省钱建议。数字照抄,不要编造。\n\n" + facts, facts);
    }

    /**
     * 统一生成路径:无 Key 降级为事实底稿,有 Key 走 LLM
     */
    private AiTextResp generate(String systemPrompt, String facts) {
        boolean mockMode = StrUtil.isBlank(properties.getApiKey());
        String output = mockMode
            ? "【测试模式】以下是本周真实数据:\n" + facts
            : llmClient.chat(List.of(LlmMessage.system(systemPrompt), LlmMessage.user(facts)));
        if (StrUtil.isBlank(output)) {
            throw new BusinessException("AI 没有返回内容,请稍后再试");
        }
        AiTextResp resp = new AiTextResp();
        resp.setText(output);
        resp.setMockMode(mockMode);
        return resp;
    }

    private static AiTextResp text(List<String> items, boolean mockMode) {
        AiTextResp resp = new AiTextResp();
        resp.setText(String.join("\n", items));
        resp.setMockMode(mockMode);
        return resp;
    }

    private LocalDate parseDate(String date) {
        if (StrUtil.isBlank(date)) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new BusinessException("日期格式应为 yyyy-MM-dd");
        }
    }

    /** [0]=未完成数, [1]=已完成数(全量口径,一期不做完成时间精确统计) */
    private int[] todoStats() {
        var all = todoService.list();
        int pending = 0;
        int done = 0;
        for (var todo : all) {
            if (todo.getDone()) {
                done++;
            } else {
                pending++;
            }
        }
        return new int[] {pending, done};
    }

    /** [0]=习惯数, [1]=今天已打卡数, [2]=最长连续, [3]=习惯名串 */
    private String[] habitStats() {
        List<ToolHabitResp> habits = habitService.list();
        int checked = 0;
        long best = 0;
        StringBuilder names = new StringBuilder();
        for (ToolHabitResp habit : habits) {
            if (Boolean.TRUE.equals(habit.getTodayChecked())) {
                checked++;
            }
            best = Math.max(best, habit.getBestStreak());
            if (names.length() > 0) {
                names.append("、");
            }
            names.append(habit.getTitle());
        }
        return new String[] {String.valueOf(habits.size()), String.valueOf(checked), String.valueOf(best), names
            .toString()};
    }
}
