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

package com.guardfind.server.notification.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.guardfind.server.notification.service.NotificationRealtimeService;
import com.guardfind.server.notification.support.NotificationConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * C 端实时通道实现（内存会话注册表 + 定向写入）
 *
 * <p>
 * 注册表为进程内 {@code userId -> sessions}：一期实时通道只承诺"在线加速"，跨节点投递（Redis 广播）留到二期，
 * 与"一期不依赖厂商推送"的定案一致。会话上限用于防止异常重连导致的连接泄漏。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Service
public class NotificationRealtimeServiceImpl implements NotificationRealtimeService {

    /**
     * 单用户会话注册表（key：C 端用户 ID）
     */
    private final Map<Long, Set<WebSocketSession>> sessionsByUserId = new ConcurrentHashMap<>();

    @Override
    public boolean register(Long userId, WebSocketSession session) {
        if (userId == null || session == null) {
            return false;
        }
        Set<WebSocketSession> sessions = sessionsByUserId.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet());
        synchronized (sessions) {
            if (sessions.contains(session)) {
                return true;
            }
            if (sessions.size() >= NotificationConstants.MAX_SESSIONS_PER_USER) {
                log.warn("C 端实时通道会话数超限，拒绝新会话，userId={}，上限={}", userId, NotificationConstants.MAX_SESSIONS_PER_USER);
                return false;
            }
            sessions.add(session);
        }
        log.info("C 端实时通道已连接，userId={}，当前会话数={}", userId, this.onlineSessions(userId));
        return true;
    }

    @Override
    public void unregister(Long userId, WebSocketSession session) {
        if (userId == null || session == null) {
            return;
        }
        sessionsByUserId.computeIfPresent(userId, (k, sessions) -> {
            synchronized (sessions) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    return null;
                }
            }
            log.debug("C 端实时通道已断开，userId={}，剩余会话数={}", userId, sessions.size());
            return sessions;
        });
    }

    @Override
    public boolean isOnline(Long userId) {
        return this.onlineSessions(userId) > 0;
    }

    @Override
    public int onlineSessions(Long userId) {
        if (userId == null) {
            return 0;
        }
        Set<WebSocketSession> sessions = sessionsByUserId.get(userId);
        if (CollUtil.isEmpty(sessions)) {
            return 0;
        }
        // 已关闭但尚未回调清理的会话在此被过滤掉，真正的摘除由 afterConnectionClosed / sendToUser 完成
        return (int)sessions.stream().filter(WebSocketSession::isOpen).count();
    }

    @Override
    public int sendToUser(Long userId, String payloadJson) {
        if (userId == null || payloadJson == null) {
            return 0;
        }
        int delivered = 0;
        Set<WebSocketSession> sessions = sessionsByUserId.get(userId);
        if (CollUtil.isEmpty(sessions)) {
            // 用户不在线属正常情况：站内信已落库，App 回前台或轮询时可见
            log.debug("用户不在线，跳过实时下发，userId={}", userId);
            return 0;
        }
        TextMessage message = new TextMessage(payloadJson);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                this.unregister(userId, session);
                continue;
            }
            try {
                // WebSocketSession 非线程安全，同一会话的并发写入必须串行
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(message);
                    }
                }
                delivered++;
            } catch (Exception e) {
                log.warn("实时下发失败（站内信已落库，不影响业务），userId={}，sessionId={}：{}", userId, session.getId(), e.getMessage());
                this.closeQuietly(session);
                this.unregister(userId, session);
            }
        }
        return delivered;
    }

    /**
     * 静默关闭会话
     *
     * @param session 会话
     */
    void closeQuietly(WebSocketSession session) {
        try {
            session.close();
        } catch (Exception e) {
            log.debug("关闭实时通道会话失败：{}", e.getMessage());
        }
    }
}
