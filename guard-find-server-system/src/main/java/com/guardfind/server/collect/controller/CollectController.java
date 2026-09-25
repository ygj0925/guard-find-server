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

package com.guardfind.server.collect.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.guardfind.server.collect.constant.CollectConstants;
import com.guardfind.server.collect.model.query.CandidateQuery;
import com.guardfind.server.collect.model.query.CollectLogQuery;
import com.guardfind.server.collect.model.req.CandidateMergeReq;
import com.guardfind.server.collect.model.req.CandidateRejectReq;
import com.guardfind.server.collect.model.req.CollectEraseReq;
import com.guardfind.server.collect.model.resp.CandidateApproveResp;
import com.guardfind.server.collect.model.resp.CandidateResp;
import com.guardfind.server.collect.model.resp.CollectEraseResp;
import com.guardfind.server.collect.model.resp.CollectLogResp;
import com.guardfind.server.collect.model.resp.CollectRunResp;
import com.guardfind.server.collect.service.CandidateService;
import com.guardfind.server.collect.service.CollectOrchestrator;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.req.IdsReq;
import top.continew.starter.extension.crud.model.resp.PageResp;
import top.continew.starter.log.annotation.Log;

/**
 * 寻亲数据采集 API（后台）
 *
 * <p>
 * 权限码与菜单种子一致：{@code system:collect:source:run}、{@code system:collect:log:list}、
 * {@code system:collect:candidate:*}。采集源 CRUD 见 {@code CollectSourceController}（由
 * {@code @CrudRequestMapping} 派生权限码）。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Tag(name = "寻亲数据采集 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/collect")
@Log(module = "寻亲采集")
public class CollectController {

    private final CollectOrchestrator collectOrchestrator;
    private final CandidateService candidateService;

    @Operation(summary = "手动触发采集", description = "受单轮页数上限保护；robots 不通过时本轮中止并留下原因")
    @Parameter(name = "sourceCode", description = "采集源标识", required = true)
    @SaCheckPermission("system:collect:source:run")
    @PostMapping("/run/{sourceCode}")
    public CollectRunResp run(@PathVariable String sourceCode,
                              @Parameter(description = "本轮最大列表页数") @RequestParam(required = false) Integer maxPages) {
        return collectOrchestrator.run(sourceCode, maxPages, CollectConstants.TRIGGER_MANUAL);
    }

    @Operation(summary = "采集批次日志", description = "分页返回页数、抓取数、新增、重复、失败、耗时与错误摘要")
    @SaCheckPermission("system:collect:log:list")
    @GetMapping("/log")
    public PageResp<CollectLogResp> pageLog(@Valid CollectLogQuery query, @Valid PageQuery pageQuery) {
        return candidateService.pageLog(query, pageQuery);
    }

    @Operation(summary = "采集候选列表", description = "按状态、来源、区划与关键词筛选；按完整度倒序便于优先审核")
    @SaCheckPermission("system:collect:candidate:list")
    @GetMapping("/candidate")
    public PageResp<CandidateResp> pageCandidate(@Valid CandidateQuery query, @Valid PageQuery pageQuery) {
        return candidateService.page(query, pageQuery);
    }

    @Operation(summary = "采集候选详情", description = "含来源链接、抓取时间、原文摘要与站内疑似重复档案")
    @Parameter(name = "id", description = "候选 ID", required = true)
    @SaCheckPermission("system:collect:candidate:get")
    @GetMapping("/candidate/{id}")
    public CandidateResp getCandidate(@PathVariable Long id) {
        return candidateService.get(id);
    }

    @Operation(summary = "审批入库", description = "幂等：重复审批返回同一档案 ID")
    @Parameter(name = "id", description = "候选 ID", required = true)
    @SaCheckPermission("system:collect:candidate:approve")
    @PostMapping("/candidate/{id}/approve")
    public CandidateApproveResp approve(@PathVariable Long id) {
        Long caseId = candidateService.approve(id, StpUtil.getLoginIdAsLong());
        return new CandidateApproveResp(caseId, "APPROVED");
    }

    @Operation(summary = "合并到既有档案", description = "仅追加来源关联，不新建档案、不改动原发布信息")
    @Parameter(name = "id", description = "候选 ID", required = true)
    @SaCheckPermission("system:collect:candidate:merge")
    @PostMapping("/candidate/{id}/merge")
    public CandidateApproveResp merge(@PathVariable Long id, @RequestBody @Valid CandidateMergeReq req) {
        Long caseId = candidateService.merge(id, req.getCaseId(), StpUtil.getLoginIdAsLong());
        return new CandidateApproveResp(caseId, "MERGED");
    }

    @Operation(summary = "驳回候选", description = "理由码必填")
    @Parameter(name = "id", description = "候选 ID", required = true)
    @SaCheckPermission("system:collect:candidate:reject")
    @PostMapping("/candidate/{id}/reject")
    public void reject(@PathVariable Long id, @RequestBody @Valid CandidateRejectReq req) {
        candidateService.reject(id, req, StpUtil.getLoginIdAsLong());
    }

    @Operation(summary = "批量删除候选", description = "软删；已生成档案的候选请走撤回清除以保证档案同步")
    @SaCheckPermission("system:collect:candidate:delete")
    @DeleteMapping("/candidate")
    public void deleteCandidate(@RequestBody @Valid IdsReq idsReq) {
        candidateService.delete(idsReq.getIds());
    }

    @Operation(summary = "按来源撤回清除", description = "候选与其生成的档案转 ERASED，并写删除审计")
    @SaCheckPermission("system:collect:candidate:erase")
    @PostMapping("/erase")
    public CollectEraseResp erase(@RequestBody @Valid CollectEraseReq req) {
        return candidateService.erase(req);
    }
}
