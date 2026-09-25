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

package com.guardfind.server.notification.service;

import com.guardfind.server.notification.model.query.AppMessageQuery;
import com.guardfind.server.notification.model.req.PreferenceUpdateReq;
import com.guardfind.server.notification.model.resp.MarkReadResp;
import com.guardfind.server.notification.model.resp.MessagePageResp;
import com.guardfind.server.notification.model.resp.PreferenceListResp;
import com.guardfind.server.notification.model.resp.UnreadResp;
import top.continew.starter.extension.crud.model.query.PageQuery;

/**
 * C 端消息与偏好查询业务接口（契约 §5）
 *
 * <p>
 * 所有方法的 {@code userId} 由控制层从 {@code AppUserContextHolder} 取当前登录用户后传入，
 * 查询侧不存在"按他人 ID 查询"的入参通道。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public interface NotificationQueryService {

    /**
     * 未读数（与列表 readStatus 同源）
     *
     * @param userId 当前登录用户 ID
     * @return 未读数
     */
    UnreadResp countUnread(Long userId);

    /**
     * 分页查询我的站内消息
     *
     * @param userId    当前登录用户 ID
     * @param query     查询条件
     * @param pageQuery 分页参数
     * @return 分页响应（列表键名为 records）
     */
    MessagePageResp page(AppMessageQuery query, PageQuery pageQuery, Long userId);

    /**
     * 标记单条消息已读（幂等；非本人消息按不存在处理）
     *
     * @param id     消息 ID
     * @param userId 当前登录用户 ID
     */
    void markRead(Long id, Long userId);

    /**
     * 全部标记已读
     *
     * @param userId 当前登录用户 ID
     * @return 本次标记数量
     */
    MarkReadResp markAllRead(Long userId);

    /**
     * 通知偏好列表（按事件目录全量返回，未配置项回显默认值）
     *
     * @param userId 当前登录用户 ID
     * @return 偏好列表
     */
    PreferenceListResp listPreference(Long userId);

    /**
     * 更新通知偏好（强制类事件不允许关闭站内信）
     *
     * @param req    请求参数
     * @param userId 当前登录用户 ID
     */
    void updatePreference(PreferenceUpdateReq req, Long userId);
}
