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

package com.guardfind.server.notification.controller;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.notification.model.query.AppMessageQuery;
import com.guardfind.server.notification.model.req.PreferenceUpdateReq;
import com.guardfind.server.notification.model.resp.MarkReadResp;
import com.guardfind.server.notification.model.resp.MessagePageResp;
import com.guardfind.server.notification.model.resp.PreferenceListResp;
import com.guardfind.server.notification.model.resp.UnreadResp;
import com.guardfind.server.notification.service.NotificationQueryService;
import top.continew.starter.extension.crud.model.query.PageQuery;

/**
 * 消息与偏好 API（C 端）
 *
 * <p>
 * 路径与字段严格对齐《天下无拐接口契约 v1》§5。{@code /app/**} 已在 {@code sa-token.extension.security.excludes}
 * 中放行后台鉴权，登录态由 {@code AppAuthInterceptor} 填充，本控制器统一以
 * {@link AppUserContextHolder#getUserIdRequired()} 取当前用户并作为查询的唯一收件人，
 * 不接受任何"查他人"的入参。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Tag(name = "消息与偏好 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app")
public class MessageController {

    private final NotificationQueryService notificationQueryService;

    @Operation(summary = "未读消息数", description = "与列表 readStatus 同源，App 断线降级时按 30 秒轮询该接口")
    @GetMapping("/message/unread")
    public UnreadResp unread() {
        return notificationQueryService.countUnread(AppUserContextHolder.getUserIdRequired());
    }

    @Operation(summary = "我的消息列表", description = "分页；可按未读与分组（AUDIT/LEAD/RESULT/SYSTEM）过滤")
    @GetMapping("/message")
    public MessagePageResp page(@Valid AppMessageQuery query,
                                @Parameter(description = "页码", required = true) @RequestParam @Min(value = 1, message = "页码不能小于 {value}") Integer page,
                                @Parameter(description = "每页条数", required = true) @RequestParam @Min(value = 1, message = "每页条数不能小于 {value}") @Max(value = 100, message = "每页条数不能大于 {value}") Integer size) {
        return notificationQueryService.page(query == null
            ? new AppMessageQuery()
            : query, new PageQuery(page, size), AppUserContextHolder.getUserIdRequired());
    }

    @Operation(summary = "标记单条消息已读", description = "幂等；非本人消息按不存在处理")
    @Parameter(name = "id", description = "消息 ID", required = true)
    @PatchMapping("/message/{id}/read")
    public void markRead(@PathVariable Long id) {
        notificationQueryService.markRead(id, AppUserContextHolder.getUserIdRequired());
    }

    @Operation(summary = "全部标记已读", description = "返回本次标记数量")
    @PatchMapping("/message/read")
    public MarkReadResp markAllRead() {
        return notificationQueryService.markAllRead(AppUserContextHolder.getUserIdRequired());
    }

    @Operation(summary = "通知偏好", description = "按事件目录全量返回，未配置项回显默认值（站内信开、短信关）")
    @GetMapping("/notification/preference")
    public PreferenceListResp preference() {
        return notificationQueryService.listPreference(AppUserContextHolder.getUserIdRequired());
    }

    @Operation(summary = "设置通知偏好", description = "审核结果类事件不支持关闭站内信")
    @PutMapping("/notification/preference")
    public void updatePreference(@RequestBody @Valid PreferenceUpdateReq req) {
        notificationQueryService.updatePreference(req, AppUserContextHolder.getUserIdRequired());
    }
}
