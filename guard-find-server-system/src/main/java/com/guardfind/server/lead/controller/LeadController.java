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

package com.guardfind.server.lead.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.guardfind.server.lead.model.query.LeadQuery;
import com.guardfind.server.lead.model.req.LeadReplyReq;
import com.guardfind.server.lead.model.req.LeadReq;
import com.guardfind.server.lead.model.req.LeadStatusReq;
import com.guardfind.server.lead.model.resp.LeadPageResp;
import com.guardfind.server.lead.model.resp.LeadReplyResp;
import com.guardfind.server.lead.model.resp.LeadResp;
import com.guardfind.server.lead.service.LeadService;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.ratelimiter.annotation.RateLimiter;
import top.continew.starter.ratelimiter.enums.LimitType;

import java.util.concurrent.TimeUnit;

/**
 * 寻人线索 API（C 端）
 *
 * <p>
 * /app/** 已在 {@code sa-token.extension.security.excludes} 中放行后台鉴权，登录态由 {@code AppUserContextHolder}
 * 判定；此处的 {@code @RateLimiter} 只是粗粒度 IP 兜底，精确维度（同用户同档案、同 IP 小时额度）在线限频器中实现。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Tag(name = "寻人线索 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app")
public class LeadController {

    private final LeadService leadService;

    @Operation(summary = "提交线索", description = "须登录且已绑定手机号；描述 10-1000 字、图片最多 3 张；同用户同档案每小时 3 条、同 IP 每小时 10 条")
    @Parameter(name = "id", description = "档案 ID", required = true)
    @RateLimiter(name = "APP:LEAD:SUBMIT", rate = 60, interval = 1, unit = TimeUnit.HOURS, type = LimitType.IP, message = "提交线索过于频繁，请稍后再试")
    @PostMapping("/find/case/{id}/lead")
    public LeadResp submit(@PathVariable Long id, @RequestBody @Valid LeadReq req) {
        return leadService.submit(req, id);
    }

    @Operation(summary = "查询档案线索列表", description = "仅档案发布人与已认证志愿者可访问；联系方式仅对档案发布人返回明文")
    @Parameter(name = "id", description = "档案 ID", required = true)
    @GetMapping("/find/case/{id}/leads")
    public LeadPageResp pageByCase(@PathVariable Long id,
                                   @Parameter(description = "页码", required = true) @RequestParam @Min(value = 1, message = "页码不能小于 {value}") Integer page,
                                   @Parameter(description = "每页条数", required = true) @RequestParam @Min(value = 1, message = "每页条数不能小于 {value}") @Max(value = 100, message = "每页条数不能大于 {value}") Integer size) {
        return leadService.pageByCase(id, new PageQuery(page, size));
    }

    @Operation(summary = "处置线索", description = "仅档案发布人可将线索置为 ACCEPTED/REJECTED/CONTACTED，处置结果站内信回执提交人")
    @Parameter(name = "id", description = "线索 ID", required = true)
    @PatchMapping("/lead/{id}/status")
    public void updateStatus(@PathVariable Long id, @RequestBody @Valid LeadStatusReq req) {
        leadService.updateStatus(id, req);
    }

    @Operation(summary = "回复线索", description = "仅档案发布人可回复，1-500 字；回复内容站内信通知提交人")
    @Parameter(name = "id", description = "线索 ID", required = true)
    @PostMapping("/lead/{id}/reply")
    public LeadReplyResp reply(@PathVariable Long id, @RequestBody @Valid LeadReplyReq req) {
        return leadService.reply(id, req);
    }

    @Operation(summary = "我提交的线索", description = "返回本人提交的全部线索及家属回复")
    @GetMapping("/lead/mine")
    public LeadPageResp mine(@Valid LeadQuery query,
                             @Parameter(description = "页码", required = true) @RequestParam @Min(value = 1, message = "页码不能小于 {value}") Integer page,
                             @Parameter(description = "每页条数", required = true) @RequestParam @Min(value = 1, message = "每页条数不能小于 {value}") @Max(value = 100, message = "每页条数不能大于 {value}") Integer size) {
        return leadService.mine(query, new PageQuery(page, size));
    }
}
