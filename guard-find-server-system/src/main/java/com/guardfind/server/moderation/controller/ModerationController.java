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

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.guardfind.server.moderation.enums.ModerationBizTypeEnum;
import com.guardfind.server.moderation.model.query.ModerationQuery;
import com.guardfind.server.moderation.model.query.ReportQueueQuery;
import com.guardfind.server.moderation.model.req.ModerationApproveReq;
import com.guardfind.server.moderation.model.req.ModerationRejectReq;
import com.guardfind.server.moderation.model.req.ReportHandleReq;
import com.guardfind.server.moderation.model.resp.ModerationItemResp;
import com.guardfind.server.moderation.model.resp.ReportQueueItemResp;
import com.guardfind.server.moderation.service.ModerationService;
import top.continew.starter.core.util.validation.ValidationUtils;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;
import top.continew.starter.log.annotation.Log;

/**
 * 内容治理 API（后台）
 *
 * <p>
 * 权限码与 {@code guardfind_business_data.sql} 中登记的菜单权限逐字一致；所有写动作留痕（谁审的、结论、时间）。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Tag(name = "内容治理 API")
@Log(module = "内容治理")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/moderation")
public class ModerationController {

    private final ModerationService moderationService;

    @Operation(summary = "待审核队列", description = "按提交时间升序；bizType 为空默认档案，高风险线索取 bizType=LEAD")
    @SaCheckPermission("system:moderation:list")
    @GetMapping("/pending")
    public PageResp<ModerationItemResp> pending(@Valid ModerationQuery query, @Valid PageQuery pageQuery) {
        return moderationService.pending(query, pageQuery);
    }

    @Operation(summary = "举报队列", description = "默认只返回待处理举报；指定状态或目标类型可按条件筛查")
    @SaCheckPermission("system:moderation:report:list")
    @GetMapping("/report")
    public PageResp<ReportQueueItemResp> reportPage(@Valid ReportQueueQuery query, @Valid PageQuery pageQuery) {
        return moderationService.reportPage(query, pageQuery);
    }

    @Operation(summary = "审核通过", description = "档案转已发布（走状态漏斗，含状态轨迹与通知）；高风险线索撤销高风险标记")
    @Parameter(name = "bizType", description = "业务类型（CASE/LEAD）", required = true)
    @Parameter(name = "id", description = "业务 ID", required = true)
    @SaCheckPermission("system:moderation:approve")
    @PostMapping("/{bizType}/{id}/approve")
    public void approve(@PathVariable String bizType,
                        @PathVariable Long id,
                        @RequestBody(required = false) @Valid ModerationApproveReq req) {
        moderationService.approve(this.parseBizType(bizType), id, req);
    }

    @Operation(summary = "审核驳回", description = "理由码必填且取自字典 gf_reject_reason，可附自由文本")
    @Parameter(name = "bizType", description = "业务类型（CASE/LEAD）", required = true)
    @Parameter(name = "id", description = "业务 ID", required = true)
    @SaCheckPermission("system:moderation:reject")
    @PostMapping("/{bizType}/{id}/reject")
    public void reject(@PathVariable String bizType,
                       @PathVariable Long id,
                       @RequestBody @Valid ModerationRejectReq req) {
        moderationService.reject(this.parseBizType(bizType), id, req);
    }

    @Operation(summary = "处置举报", description = "结论通知举报人与被举报人，可选登记 MUTE/BAN 处置")
    @Parameter(name = "id", description = "举报 ID", required = true)
    @SaCheckPermission("system:moderation:report:handle")
    @PostMapping("/report/{id}/handle")
    public void handleReport(@PathVariable Long id, @RequestBody @Valid ReportHandleReq req) {
        moderationService.handleReport(id, req);
    }

    /**
     * 路径变量解析为业务类型（大小写不敏感，非法取值直接给出可选范围）
     *
     * @param bizType 业务类型
     * @return 枚举
     */
    private ModerationBizTypeEnum parseBizType(String bizType) {
        ModerationBizTypeEnum type = ModerationBizTypeEnum.of(bizType);
        ValidationUtils.throwIfNull(type, "业务类型仅支持 CASE/LEAD/COLLECT_CANDIDATE");
        return type;
    }
}
