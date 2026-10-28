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

package com.guardfind.server.ledger.controller;

import io.swagger.v3.oas.annotations.Operation;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.ratelimiter.annotation.RateLimiter;
import top.continew.starter.ratelimiter.enums.LimitType;

import com.guardfind.server.ledger.model.req.LedgerRecordReq;
import com.guardfind.server.ledger.model.resp.LedgerPageResp;
import com.guardfind.server.ledger.model.resp.LedgerSummaryResp;
import com.guardfind.server.ledger.service.LedgerService;

import java.util.concurrent.TimeUnit;

/**
 * 记账 API（C 端）
 *
 * <p>
 * /app/** 已在 sa-token 配置中放行后台鉴权，登录态由 {@code AppUserContextHolder} 判定；
 * 数据全部是个人流水，未登录会在 Service 层抛 401。
 * </p>
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Tag(name = "记账 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    @Operation(summary = "记一笔", description = "须登录；金额为正数最多两位小数，分类码必须与收支类型匹配")
    @RateLimiter(name = "APP:LEDGER:CREATE", rate = 60, interval = 1, unit = TimeUnit.MINUTES, type = LimitType.IP, message = "记账太频繁，请稍后再试")
    @PostMapping("/record")
    public Long create(@RequestBody @Valid LedgerRecordReq req) {
        return ledgerService.create(req);
    }

    @Operation(summary = "修改记账记录", description = "仅本人记录可修改")
    @Parameter(name = "id", description = "记录 ID", required = true)
    @PutMapping("/record/{id}")
    public void update(@PathVariable Long id, @RequestBody @Valid LedgerRecordReq req) {
        ledgerService.update(id, req);
    }

    @Operation(summary = "删除记账记录", description = "仅本人记录可删除")
    @Parameter(name = "id", description = "记录 ID", required = true)
    @DeleteMapping("/record/{id}")
    public void delete(@PathVariable Long id) {
        ledgerService.delete(id);
    }

    @Operation(summary = "分页查询记账记录", description = "按记账日期倒序；month 为 yyyy-MM 时只查该月")
    @Parameter(name = "month", description = "月份（yyyy-MM，可空）")
    @GetMapping("/record/page")
    public LedgerPageResp page(@RequestParam(required = false) String month,
                               @RequestParam(required = false) Long groupId,
                               PageQuery pageQuery) {
        return ledgerService.page(month, groupId, pageQuery);
    }

    @Operation(summary = "周报/月报/年报汇总", description = "period=week|month|year；date 为区间内锚点日（缺省今天）；趋势空档补零")
    @Parameter(name = "period", description = "汇总周期（week/month/year）", required = true)
    @Parameter(name = "date", description = "锚点日（yyyy-MM-dd，可空）")
    @GetMapping("/summary")
    public LedgerSummaryResp summary(@RequestParam @Pattern(regexp = "^(week|month|year)$", message = "period 仅支持 week/month/year") String period,
                                     @RequestParam(required = false) String date,
                                     @RequestParam(required = false) Long groupId) {
        return ledgerService.summary(period, date, groupId);
    }
}
