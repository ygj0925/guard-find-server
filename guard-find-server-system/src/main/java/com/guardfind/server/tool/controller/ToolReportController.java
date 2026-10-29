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

package com.guardfind.server.tool.controller;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.starter.ratelimiter.annotation.RateLimiter;
import top.continew.starter.ratelimiter.enums.LimitType;

import com.guardfind.server.tool.model.req.ToolReportCommentReq;
import com.guardfind.server.tool.model.req.ToolReportCreateReq;
import com.guardfind.server.tool.model.req.ToolReportEntryReq;
import com.guardfind.server.tool.model.resp.ToolReportCommentResp;
import com.guardfind.server.tool.model.resp.ToolReportEntryResp;
import com.guardfind.server.tool.model.resp.ToolReportResp;
import com.guardfind.server.tool.service.ToolReportService;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 月度工作汇报 API(C 端)。
 * 权限矩阵:查看/评论=成员;填报=REPORTER 本人;草稿=仅本人;管理=OWNER;非成员一律 NOT_FOUND。
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Tag(name = "月度工作汇报 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/tool/report")
public class ToolReportController {

    private final ToolReportService reportService;

    @Operation(summary = "创建汇报活动", description = "发起人自动 OWNER;填报人/接收人批量指定;填报人收到站内信")
    @RateLimiter(name = "APP:TOOL:REPORT", rate = 20, interval = 1, unit = TimeUnit.HOURS, type = LimitType.IP)
    @PostMapping
    public Long create(@RequestBody @Valid ToolReportCreateReq req) {
        return reportService.create(req);
    }

    @Operation(summary = "我相关的汇报活动", description = "含我的角色与我的提交状态")
    @GetMapping("/mine")
    public List<ToolReportResp> mine() {
        return reportService.myActivities();
    }

    @Operation(summary = "活动详情", description = "含全部填报人提交状态;仅成员")
    @Parameter(name = "id", description = "活动 ID", required = true)
    @GetMapping("/{id}")
    public ToolReportResp detail(@PathVariable Long id) {
        return reportService.detail(id);
    }

    @Operation(summary = "读取月报", description = "草稿仅本人可读;已提交全成员可读")
    @Parameter(name = "id", description = "活动 ID", required = true)
    @Parameter(name = "entryUserId", description = "填报人用户 ID", required = true)
    @GetMapping("/{id}/entry/{entryUserId}")
    public ToolReportEntryResp entry(@PathVariable Long id, @PathVariable Long entryUserId) {
        return reportService.entry(id, entryUserId);
    }

    @Operation(summary = "保存我的月报", description = "submit=true 提交(全成员可读,记录迟交),false 存草稿(仅本人)")
    @Parameter(name = "id", description = "活动 ID", required = true)
    @RateLimiter(name = "APP:TOOL:REPORT:SAVE", rate = 60, interval = 1, unit = TimeUnit.MINUTES, type = LimitType.IP)
    @PutMapping("/{id}/entry")
    public void saveEntry(@PathVariable Long id, @RequestBody @Valid ToolReportEntryReq req) {
        reportService.saveEntry(id, req);
    }

    @Operation(summary = "月报评论列表", description = "升序,含楼中楼关系;仅成员")
    @Parameter(name = "id", description = "活动 ID", required = true)
    @Parameter(name = "entryUserId", description = "填报人用户 ID", required = true)
    @GetMapping("/{id}/entry/{entryUserId}/comment")
    public List<ToolReportCommentResp> comments(@PathVariable Long id, @PathVariable Long entryUserId) {
        return reportService.comments(id, entryUserId);
    }

    @Operation(summary = "发表评论/回复", description = "仅成员;支持楼中楼;作者与被回复人收站内信")
    @Parameter(name = "id", description = "活动 ID", required = true)
    @Parameter(name = "entryUserId", description = "填报人用户 ID", required = true)
    @RateLimiter(name = "APP:TOOL:REPORT:CMT", rate = 30, interval = 1, unit = TimeUnit.MINUTES, type = LimitType.IP)
    @PostMapping("/{id}/entry/{entryUserId}/comment")
    public Long comment(@PathVariable Long id,
                        @PathVariable Long entryUserId,
                        @RequestBody @Valid ToolReportCommentReq req) {
        return reportService.comment(id, entryUserId, req);
    }

    @Operation(summary = "添加成员", description = "仅发起人;role 2=填报 3=接收;幂等")
    @Parameter(name = "id", description = "活动 ID", required = true)
    @Parameter(name = "role", description = "2 或 3", required = true)
    @PostMapping("/{id}/members")
    public void addMembers(@PathVariable Long id, @RequestParam List<Long> userIds, @RequestParam Integer role) {
        reportService.addMembers(id, userIds, role);
    }

    @Operation(summary = "移除成员", description = "仅发起人;不能移除自己;月报保留")
    @Parameter(name = "id", description = "活动 ID", required = true)
    @DeleteMapping("/{id}/member/{memberUserId}")
    public void removeMember(@PathVariable Long id, @PathVariable Long memberUserId) {
        reportService.removeMember(id, memberUserId);
    }

    @Operation(summary = "删除活动", description = "仅发起人;级联删成员/月报/评论")
    @Parameter(name = "id", description = "活动 ID", required = true)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        reportService.delete(id);
    }
}
