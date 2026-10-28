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

package com.guardfind.server.ai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.starter.ratelimiter.annotation.RateLimiter;
import top.continew.starter.ratelimiter.enums.LimitType;

import com.guardfind.server.ai.model.resp.AiTextResp;
import com.guardfind.server.ai.service.AiInsightService;

import java.util.concurrent.TimeUnit;

/**
 * AI 洞察 API:周报 / 目标拆解 / 记账点评。
 * 数据事实由服务端统计,模型只负责表述;未配置 Key 时降级为测试模式。
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Tag(name = "AI 洞察 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/ai")
public class AiInsightController {

    private final AiInsightService insightService;

    @Operation(summary = "个人周报", description = "本周记账汇总+待办+打卡 → 一段周报;date 为周内锚点日")
    @Parameter(name = "date", description = "yyyy-MM-dd,可空=今天")
    @RateLimiter(name = "APP:AI:REPORT", rate = 10, interval = 1, unit = TimeUnit.MINUTES, type = LimitType.IP)
    @PostMapping("/weekly-report")
    public AiTextResp weeklyReport(@RequestParam(required = false) String date) {
        return insightService.weeklyReport(date);
    }

    @Operation(summary = "目标拆解", description = "把一个目标拆成 3-5 个可执行小步骤(JSON 数组文本)")
    @RateLimiter(name = "APP:AI:DECOMP", rate = 20, interval = 1, unit = TimeUnit.MINUTES, type = LimitType.IP)
    @PostMapping("/decompose")
    public AiTextResp decompose(@RequestBody @jakarta.validation.Valid DecomposeReq req) {
        return insightService.decompose(req.content());
    }

    @Operation(summary = "记账周期点评", description = "period=week|month;date 为锚点日")
    @Parameter(name = "period", description = "week 或 month,缺省 month")
    @RateLimiter(name = "APP:AI:INSIGHT", rate = 10, interval = 1, unit = TimeUnit.MINUTES, type = LimitType.IP)
    @PostMapping("/ledger-insight")
    public AiTextResp ledgerInsight(@RequestParam(required = false, defaultValue = "month") String period,
                                    @RequestParam(required = false) String date) {
        return insightService.ledgerInsight(period, date);
    }

    /**
     * 拆解请求体
     */
    public record DecomposeReq(@NotBlank(message = "目标内容不能为空") @Size(max = 200, message = "目标不能超过 200 字") String content) {
    }
}
