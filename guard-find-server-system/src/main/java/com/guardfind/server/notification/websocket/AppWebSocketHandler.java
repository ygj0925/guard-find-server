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

package com.guardfind.server.notification.websocket;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardfind.server.notification.model.NotificationFrame;
import com.guardfind.server.notification.service.NotificationRealtimeService;
import com.guardfind.server.notification.support.NotificationConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Objects;

/**
 * C 端实时通道处理器（{@code /app/websocket}）
 *
 * <p>
 * 会话身份来自 {@link AppWebSocketHandshakeInterceptor} 在握手阶段写入的用户 ID，运行期不再解析 token；
 * 连接建立即登记到 {@code userId -> sessions} 注册表，后续所有下发都按该注册表定向，不做广播。
 * </p>
 *
 * <p>
 * <b>注意：本类不得声明为 Spring Bean</b>——模板的
 * {@code WebSocketAutoConfiguration#webSocketHandler} 上带 {@code @ConditionalOnMissingBean(WebSocketHandler.class)}，
 * 一旦本类进入容器就会顶掉后台 {@code /websocket} 的处理器。故由 {@code AppWebSocketConfiguration} 直接实例化。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@RequiredArgsConstructor
public class AppWebSocketHandler extends TextWebSocketHandler {

    private final NotificationRealtimeService notificationRealtimeService;

    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = this.resolveUserId(session);
        if (userId == null) {
            // 理论上不会发生（握手已鉴权），此处兜底拒绝匿名会话
            this.closeQuietly(session, CloseStatus.POLICY_VIOLATION.withReason("未识别的登录态"));
            return;
        }
        if (!notificationRealtimeService.register(userId, session)) {
            this.closeQuietly(session, CloseStatus.POLICY_VIOLATION.withReason("会话数已达上限"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if (this.isPing(message.getPayload())) {
            this.sendPong(session);
            return;
        }
        // 一期下行只有站内信、上行只有心跳；未知上行帧忽略不断连，避免客户端小版本差异导致掉线
        log.debug("C 端实时通道收到未处理的帧，sessionId={}，payload={}", session.getId(), StrUtil.maxLength(message
            .getPayload(), 128));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = this.resolveUserId(session);
        if (userId != null) {
            notificationRealtimeService.unregister(userId, session);
        }
        log.info("C 端实时通道已关闭，sessionId={}，code={}", session.getId(), status.getCode());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long userId = this.resolveUserId(session);
        if (userId != null) {
            notificationRealtimeService.unregister(userId, session);
        }
        log.warn("C 端实时通道传输异常，sessionId={}：{}", session.getId(), exception == null ? null : exception.getMessage());
        this.closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    /**
     * 是否为心跳帧：兼容裸 {@code ping} 与契约形态 {@code {"type":"ping"}}
     *
     * @param payload 帧内容
     * @return true：心跳
     */
    boolean isPing(String payload) {
        if (StrUtil.isBlank(payload)) {
            return false;
        }
        if (NotificationConstants.FRAME_TYPE_PING.equalsIgnoreCase(StrUtil.trim(payload))) {
            return true;
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            return NotificationConstants.FRAME_TYPE_PING.equalsIgnoreCase(node.path("type").asText());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 回心跳响应帧
     *
     * @param session 会话
     */
    void sendPong(WebSocketSession session) {
        try {
            String pong = objectMapper.writeValueAsString(NotificationFrame.pong());
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(pong));
                }
            }
        } catch (Exception e) {
            log.warn("回心跳失败，sessionId={}：{}", session.getId(), e.getMessage());
        }
    }

    /**
     * 取握手阶段登记的用户 ID
     *
     * @param session 会话
     * @return 用户 ID，未识别时为 null
     */
    Long resolveUserId(WebSocketSession session) {
        Object userId = session == null || session.getAttributes() == null
            ? null
            : session.getAttributes().get(NotificationConstants.WS_USER_ID_ATTR);
        return userId instanceof Long id ? id : null;
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session != null && session.isOpen()) {
                session.close(Objects.requireNonNullElse(status, CloseStatus.SERVER_ERROR));
            }
        } catch (Exception e) {
            log.debug("关闭 C 端实时通道会话失败：{}", e.getMessage());
        }
    }
}
