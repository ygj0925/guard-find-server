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

package com.guardfind.server.findcase.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.guardfind.server.findcase.model.query.CaseMineQuery;
import com.guardfind.server.findcase.model.req.CaseActionReq;
import com.guardfind.server.findcase.model.req.CaseFoundReq;
import com.guardfind.server.findcase.model.req.CaseReq;
import com.guardfind.server.findcase.model.req.CaseSubmitReq;
import com.guardfind.server.findcase.model.resp.CaseDetailResp;
import com.guardfind.server.findcase.model.resp.CaseMinePageResp;
import com.guardfind.server.findcase.model.resp.CasePublicResp;
import com.guardfind.server.findcase.service.CaseService;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.ratelimiter.annotation.RateLimiter;
import top.continew.starter.ratelimiter.enums.LimitType;

import java.util.concurrent.TimeUnit;

/**
 * 寻人档案 API（C 端）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Tag(name = "寻人档案 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/find/case")
public class CaseController {

    private final CaseService caseService;

    @Operation(summary = "创建寻人档案", description = "submit=false 存草稿，submit=true 直接进入待审核")
    @PostMapping
    public CaseDetailResp create(@RequestBody @Valid CaseReq req) {
        return caseService.create(req);
    }

    @Operation(summary = "修改寻人档案", description = "草稿/驳回/撤回可全量修改；已发布仅可补充信息")
    @Parameter(name = "id", description = "档案 ID", required = true)
    @PutMapping("/{id}")
    public CaseDetailResp update(@RequestBody @Valid CaseReq req, @PathVariable Long id) {
        return caseService.update(req, id);
    }

    @SaIgnore
    @Operation(summary = "查询寻人档案公开详情", description = "脱敏视图；不可对外可见的状态返回\"信息不存在或已下架\"")
    @Parameter(name = "id", description = "档案 ID", required = true)
    @RateLimiter(name = "APP:CASE:PUBLIC", rate = 120, interval = 1, unit = TimeUnit.MINUTES, type = LimitType.IP, message = "访问过于频繁，请稍后再试")
    @GetMapping("/{id}")
    public CasePublicResp getPublic(@PathVariable Long id) {
        return caseService.getPublic(id);
    }

    @Operation(summary = "查询寻人档案完整详情", description = "本人/志愿者/审核可见，返回联系方式并写入访问留痕")
    @Parameter(name = "id", description = "档案 ID", required = true)
    @GetMapping("/{id}/full")
    public CaseDetailResp getFull(@PathVariable Long id) {
        return caseService.getFull(id);
    }

    @Operation(summary = "我的发布", description = "分页返回完整视图，并附各状态计数")
    @GetMapping("/mine")
    public CaseMinePageResp mine(@Valid CaseMineQuery query,
                                 @Parameter(description = "页码", required = true) @RequestParam @Min(value = 1, message = "页码不能小于 {value}") Integer page,
                                 @Parameter(description = "每页条数", required = true) @RequestParam @Min(value = 1, message = "每页条数不能小于 {value}") @Max(value = 100, message = "每页条数不能大于 {value}") Integer size) {
        return caseService.mine(query, new PageQuery(page, size));
    }

    @Operation(summary = "提交审核", description = "草稿/驳回/撤回 → 待审核，写版本快照")
    @Parameter(name = "id", description = "档案 ID", required = true)
    @PostMapping("/{id}/submit")
    public CaseDetailResp submit(@PathVariable Long id, @RequestBody(required = false) @Valid CaseSubmitReq req) {
        return caseService.submit(id, req);
    }

    @Operation(summary = "确认找回", description = "必须提供确认依据 POLICE/GUARDIAN/DNA")
    @Parameter(name = "id", description = "档案 ID", required = true)
    @PostMapping("/{id}/found")
    public void found(@PathVariable Long id, @RequestBody @Valid CaseFoundReq req) {
        caseService.found(id, req);
    }

    @Operation(summary = "续期", description = "重置 90 天时效；已归档档案续期后重新发布")
    @Parameter(name = "id", description = "档案 ID", required = true)
    @PostMapping("/{id}/renew")
    public void renew(@PathVariable Long id, @RequestBody(required = false) @Valid CaseActionReq req) {
        caseService.renew(id, req);
    }

    @Operation(summary = "撤回发布", description = "已发布 → 已撤回")
    @Parameter(name = "id", description = "档案 ID", required = true)
    @PostMapping("/{id}/withdraw")
    public void withdraw(@PathVariable Long id, @RequestBody(required = false) @Valid CaseActionReq req) {
        caseService.withdraw(id, req);
    }

    @Operation(summary = "删除档案", description = "响应删除权：转为已删除并清除识别信息与媒体文件")
    @Parameter(name = "id", description = "档案 ID", required = true)
    @PostMapping("/{id}/erase")
    public void erase(@PathVariable Long id, @RequestBody(required = false) @Valid CaseActionReq req) {
        caseService.erase(id, req);
    }
}
