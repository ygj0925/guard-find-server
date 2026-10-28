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

package com.guardfind.server.ai.support;

import cn.hutool.core.util.StrUtil;
import com.guardfind.server.ledger.model.resp.LedgerSummaryResp;

import java.util.StringJoiner;

/**
 * AI 洞察的事实底稿(纯函数):把服务端算好的统计数据拼成给模型的提示文本。
 * 模型只负责"写成话",数字一律以这里为准,避免模型编造。
 *
 * @author guardfind
 * @since 2026/10/28
 */
public final class AiStatsTextBuilder {

    private AiStatsTextBuilder() {
    }

    /**
     * 记账汇总事实
     */
    public static String ledgerFacts(LedgerSummaryResp summary, String periodLabel) {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("周期:" + periodLabel + "(" + summary.getStartDate() + " ~ " + summary.getEndDate() + ")");
        joiner.add("总支出:" + summary.getTotalExpense() + " 元");
        joiner.add("总收入:" + summary.getTotalIncome() + " 元");
        joiner.add("结余:" + summary.getBalance() + " 元");
        joiner.add("记账笔数:" + summary.getRecordCount());
        if (summary.getByCategory() != null && !summary.getByCategory().isEmpty()) {
            StringJoiner categories = new StringJoiner("、");
            summary.getByCategory()
                .stream()
                .limit(5)
                .forEach(stat -> categories.add(stat.getCategory() + " " + stat
                    .getExpense() + " 元(" + AiStatsTextBuilder.percent(stat.getRatio()) + ")"));
            joiner.add("支出分类 TOP5:" + categories);
        } else {
            joiner.add("该周期没有支出记录");
        }
        return joiner.toString();
    }

    /**
     * 待办 + 习惯事实
     */
    public static String lifeFacts(int pendingTodos,
                                   int doneTodosThisWeek,
                                   int habitCount,
                                   int checkedTodayCount,
                                   int bestStreak,
                                   String habitNames) {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("待办:未完成 " + pendingTodos + " 项,本周完成 " + doneTodosThisWeek + " 项");
        if (habitCount == 0) {
            joiner.add("习惯打卡:暂无习惯");
        } else {
            joiner
                .add("习惯打卡:共 " + habitCount + " 个习惯,今天已打卡 " + checkedTodayCount + " 个," + "最长连续 " + bestStreak + " 天(" + StrUtil
                    .blankToDefault(habitNames, "无") + ")");
        }
        return joiner.toString();
    }

    private static String percent(java.math.BigDecimal ratio) {
        return ratio.multiply(java.math.BigDecimal.valueOf(100)).setScale(1, java.math.RoundingMode.HALF_UP) + "%";
    }
}
