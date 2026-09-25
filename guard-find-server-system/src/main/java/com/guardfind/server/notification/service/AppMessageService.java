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

import com.guardfind.server.notification.enums.NotificationEventEnum;

/**
 * App 站内消息服务
 *
 * <p>
 * 约定：先落库、后推送；推送异常 MUST NOT 影响（不回滚）业务事务，因此实现类以
 * {@code Propagation.REQUIRED} 参与调用方事务，推送部分单独 try/catch。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public interface AppMessageService {

    /**
     * 发送一条站内消息（并按需实时下发）
     *
     * @param receiverId 接收人（App 用户 ID）
     * @param event      事件
     * @param bizType    业务类型（FIND_CASE/LEAD/REPORT/COLLECT）
     * @param bizId      业务 ID，用于构造跳转路径
     * @param title      标题
     * @param content    内容
     */
    void send(Long receiverId, NotificationEventEnum event, String bizType, Long bizId, String title, String content);

    /**
     * 未读数量
     *
     * @param receiverId 接收人
     * @return 未读数
     */
    long countUnread(Long receiverId);
}
