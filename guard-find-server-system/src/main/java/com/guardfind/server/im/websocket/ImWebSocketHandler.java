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

package com.guardfind.server.im.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.app.util.StpAppUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * IM WebSocket Handler:处理实时消息推送和接收。
 *
 * <p>
 * 协议(纯 JSON 文本帧):
 * 客户端心跳: {"action":"ping"}
 * 服务端推送: {"action":"new_message","conversationId":"123","senderId":"456","content":"hello","messageId":"789"}
 * 服务端推送: {"action":"admin_message","messageId":"789","content":"hello","adminName":"官方客服"}
 * </p>
 *
 * <p>
 * 连接鉴权:握手阶段通过 URL 参数 token 验证 Sa-Token 和账号状态。
 * 连接建立后,session 与 userId、原始 token 绑定，每次心跳和推送重新鉴权。
 * 断线后客户端自动降级为 REST 轮询。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImWebSocketHandler extends TextWebSocketHandler {

    /** userId → 该用户所有活跃的 WebSocketSession(可能多设备) */
    private static final Map<Long, Set<WebSocketSession>> USER_SESSIONS = new ConcurrentHashMap<>();

    private final AppUserMapper appUserMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        synchronized (session) {
            if (!validateSession(session))
                return;
            Long userId = getUserId(session);
            USER_SESSIONS.compute(userId, (id, sessions) -> {
                if (sessions == null)
                    sessions = new CopyOnWriteArraySet<>();
                sessions.add(session);
                return sessions;
            });
            log.info("[ws:im] 用户 {} 连接,当前在线会话数 {}", userId, USER_SESSIONS.size());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        synchronized (session) {
            removeSession(session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        synchronized (session) {
            if (!validateSession(session))
                return;
            try {
                JsonNode node = objectMapper.readTree(message.getPayload());
                String action = node.path("action").asText("");
                if ("ping".equals(action)) {
                    session.sendMessage(new TextMessage("{\"action\":\"pong\"}"));
                }
                // 发送仍走 REST，WS 仅用于接收推送及心跳
            } catch (Exception e) {
                log.warn("[ws:im] 处理消息异常: {}", e.getMessage());
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        synchronized (session) {
            closeSession(session, CloseStatus.SERVER_ERROR);
        }
    }

    /**
     * 向指定用户的所有活跃连接推送新消息
     */
    public void pushNewMessage(Long targetUserId, Long conversationId, Long senderId, String content, Long messageId) {
        Set<WebSocketSession> sessions = USER_SESSIONS.get(targetUserId);
        if (sessions == null || sessions.isEmpty())
            return;
        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map
                .of("action", "new_message", "conversationId", conversationId.toString(), "senderId", senderId.toString(), "content", content, "messageId", messageId.toString()));
        } catch (Exception e) {
            log.warn("[ws:im] 序列化推送消息失败: {}", e.getMessage());
            return;
        }
        TextMessage textMessage = new TextMessage(payload);
        for (WebSocketSession session : sessions) {
            sendAuthenticated(session, textMessage);
        }
    }

    /**
     * 向指定用户推送后台客服消息(管理员与用户沟通)
     */
    public void pushAdminMessage(Long targetUserId, Long messageId, String content, String adminName) {
        Set<WebSocketSession> sessions = USER_SESSIONS.get(targetUserId);
        if (sessions == null || sessions.isEmpty())
            return;
        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map
                .of("action", "admin_message", "messageId", messageId.toString(), "content", content, "adminName", adminName == null
                    ? "官方客服"
                    : adminName));
        } catch (Exception e) {
            log.warn("[ws:im] 序列化客服消息失败: {}", e.getMessage());
            return;
        }
        TextMessage textMessage = new TextMessage(payload);
        for (WebSocketSession session : sessions) {
            sendAuthenticated(session, textMessage);
        }
    }

    private void sendAuthenticated(WebSocketSession session, TextMessage message) {
        // 与 ping 共用 session 锁，鉴权也必须在锁内，避免排队后使用过期结果。
        synchronized (session) {
            if (!validateSession(session))
                return;
            try {
                session.sendMessage(message);
            } catch (Exception e) {
                log.warn("[ws:im] 推送消息失败: {}", e.getMessage());
                closeSession(session, CloseStatus.SERVER_ERROR);
            }
        }
    }

    /** 调用方持有 session 锁；每次使用连接都重新验证原始 token 和账号状态。 */
    private boolean validateSession(WebSocketSession session) {
        if (!session.isOpen()) {
            removeSession(session);
            return false;
        }
        Long userId = getUserId(session);
        Object token = session.getAttributes().get("token");
        try {
            if (userId != null && token instanceof String tokenValue && !tokenValue.isBlank()
                && userId.equals(StpAppUtil.getLoginId(tokenValue))) {
                AppUserDO user = appUserMapper.selectById(userId);
                if (user != null && Integer.valueOf(1).equals(user.getAccountStatus()))
                    return true;
            }
        } catch (Exception e) {
            log.debug("[ws:im] token 或账号验证失败");
        }
        closeSession(session, CloseStatus.NOT_ACCEPTABLE);
        return false;
    }

    private void closeSession(WebSocketSession session, CloseStatus status) {
        removeSession(session);
        try {
            session.close(status);
        } catch (Exception e) {
            log.debug("[ws:im] 关闭连接失败");
        }
    }

    private void removeSession(WebSocketSession session) {
        // 清理只依赖握手时保存的 userId，不能再次依赖已撤销或已变更归属的 token。
        Long userId = getUserId(session);
        if (userId != null) {
            USER_SESSIONS.computeIfPresent(userId, (id, sessions) -> {
                sessions.remove(session);
                return sessions.isEmpty() ? null : sessions;
            });
        }
    }

    /**
     * 从 WebSocketSession attributes 中获取原始用户 ID，仅用于连接归属和清理，不代表鉴权通过。
     */
    private Long getUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get("userId");
        return userId instanceof Long ? (Long)userId : null;
    }
}
