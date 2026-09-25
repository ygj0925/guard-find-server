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

package com.guardfind.server.moderation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.guardfind.server.moderation.model.req.ReportReq;
import com.guardfind.server.moderation.model.resp.ReportPageResp;
import com.guardfind.server.moderation.model.resp.ReportResp;
import com.guardfind.server.moderation.service.ReportService;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.ratelimiter.annotation.RateLimiter;
import top.continew.starter.ratelimiter.enums.LimitType;

import java.util.concurrent.TimeUnit;

/**
 * 举报 API（C 端）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Tag(name = "举报 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/report")
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "提交举报", description = "同一举报人对同一目标仅保留一条记录，重复提交累加 hitCount；理由取值来自字典 gf_report_reason")
    @RateLimiter(name = "APP:REPORT", rate = 30, interval = 1, unit = TimeUnit.HOURS, type = LimitType.IP, message = "提交举报过于频繁，请稍后再试")
    @PostMapping
    public ReportResp report(@RequestBody @Valid ReportReq req) {
        return reportService.report(req);
    }

    @Operation(summary = "我提交的举报", description = "分页返回本人提交的举报及其处理结果")
    @GetMapping("/mine")
    public ReportPageResp mine(@Parameter(description = "页码", required = true) @RequestParam @Min(value = 1, message = "页码不能小于 {value}") Integer page,
                               @Parameter(description = "每页条数", required = true) @RequestParam @Min(value = 1, message = "每页条数不能小于 {value}") @Max(value = 100, message = "每页条数不能大于 {value}") Integer size) {
        return reportService.mine(new PageQuery(page, size));
    }
}
