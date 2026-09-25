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

import org.springframework.web.socket.WebSocketSession;

/**
 * C 端实时通道服务（会话登记 + 定向下发）
 *
 * <p>
 * 模板自带的 {@code /websocket} 以 <b>后台 Sa-Token 域的 token</b> 作为会话标识（见
 * {@code WebSocketClientServiceImpl#getClientId} 与 {@code MessageServiceImpl} 中
 * {@code WebSocketUtils.sendMessage(StpUtil.getTokenValueByLoginId(userId), payload)} 的寻址键），
 * C 端 {@code loginType=app} 的 token 在其会话表中不存在，故 C 端使用独立的 {@code /app/websocket} 端点，
 * 由本服务维护 {@code userId -> sessions} 注册表并按用户定向推送，MUST NOT 广播后靠客户端过滤。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public interface NotificationRealtimeService {

    /**
     * 登记会话（握手鉴权通过后调用）
     *
     * @param userId  C 端用户 ID
     * @param session 会话
     * @return true：登记成功；false：超出单用户会话上限（调用方应拒绝该会话）
     */
    boolean register(Long userId, WebSocketSession session);

    /**
     * 注销会话（连接关闭或传输异常时调用）
     *
     * @param userId  C 端用户 ID
     * @param session 会话
     */
    void unregister(Long userId, WebSocketSession session);

    /**
     * 用户是否在线（存在可用会话）
     *
     * @param userId C 端用户 ID
     * @return true：在线
     */
    boolean isOnline(Long userId);

    /**
     * 用户在线会话数（多端登录）
     *
     * @param userId C 端用户 ID
     * @return 会话数
     */
    int onlineSessions(Long userId);

    /**
     * 按用户定向下发帧（只写入该 userId 名下的会话）
     *
     * <p>
     * 本方法 MUST NOT 抛出异常：实时通道是"尽力而为"的加速通道，未送达不影响站内信已落库的事实，
     * 调用方（业务事务）不应因推送失败而回滚。
     * </p>
     *
     * @param userId      C 端用户 ID
     * @param payloadJson 帧 JSON
     * @return 实际写入的会话数（0 表示用户不在线，属正常情况）
     */
    int sendToUser(Long userId, String payloadJson);
}
