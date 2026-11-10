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

package com.guardfind.server.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.app.util.StpAppUtil;
import com.guardfind.server.im.websocket.ImHandshakeInterceptor;
import com.guardfind.server.im.websocket.ImWebSocketHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** WebSocket 专用无容器安全回归测试，不依赖数据库或 Redis。 */
class ImWebSocketSecurityTest {

    private static final long USER_ID = 1789000000000000001L;
    private static final String TOKEN = "original-app-token";
    private final AppUserMapper users = mock(AppUserMapper.class);
    private final ImWebSocketHandler handler = new ImWebSocketHandler(users);
    private final ImHandshakeInterceptor handshake = new ImHandshakeInterceptor(users);
    private final List<WebSocketSession> sessions = new ArrayList<>();
    private AppUserDO user;

    @BeforeEach
    void setUp() {
        user = new AppUserDO();
        user.setId(USER_ID);
        user.setAccountStatus(1);
        when(users.selectById(USER_ID)).thenReturn(user);
    }

    @AfterEach
    void cleanUp() {
        // 不清空全局注册表，只按真实断线入口清理本测试拥有的 session。
        sessions.forEach(session -> handler.afterConnectionClosed(session, CloseStatus.NORMAL));
    }

    @Test
    void validHandshakeBindsOriginalTokenAndUserId() {
        try (var app = validToken()) {
            Map<String, Object> attributes = new HashMap<>();
            assertTrue(handshake(TOKEN, attributes));
            assertEquals(USER_ID, attributes.get("userId"));
            assertEquals(TOKEN, attributes.get("token"));
            app.verify(() -> StpAppUtil.getLoginId(TOKEN));
            verify(users).selectById(USER_ID);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void handshakeRejectsMissingOrBlankToken(String token) {
        try (var app = validToken()) {
            Map<String, Object> attributes = new HashMap<>();
            assertFalse(handshake(token, attributes));
            assertTrue(attributes.isEmpty());
            app.verifyNoInteractions();
            verifyNoInteractions(users);
        }
    }

    @ParameterizedTest
    @EnumSource(value = InvalidState.class, names = {"CHANGED_USER", "MISSING_TOKEN"}, mode = EnumSource.Mode.EXCLUDE)
    void handshakeRejectsInvalidTokenOrAccount(InvalidState state) {
        try (var app = validToken()) {
            invalidate(state, app, null);
            Map<String, Object> attributes = new HashMap<>();
            assertFalse(handshake(TOKEN, attributes));
            assertTrue(attributes.isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void handshakeOnlyAcceptsEnabledAccount(int status) {
        try (var app = validToken()) {
            user.setAccountStatus(status);
            assertFalse(handshake(TOKEN, new HashMap<>()));
        }
    }

    static Stream<Arguments> invalidOperations() {
        return Arrays.stream(InvalidState.values())
            .flatMap(state -> Arrays.stream(Operation.values()).map(operation -> Arguments.of(state, operation)));
    }

    @ParameterizedTest
    @MethodSource("invalidOperations")
    void eachEntryRejectsInvalidSessionAndRemovesIt(InvalidState state, Operation operation) throws Exception {
        try (var app = validToken()) {
            WebSocketSession session = session(TOKEN);
            if (operation != Operation.CONNECT)
                handler.afterConnectionEstablished(session);
            invalidate(state, app, session);

            perform(operation, session);

            verify(session, never()).sendMessage(any());
            verify(session).close(CloseStatus.NOT_ACCEPTABLE);
            // close mock 故意仍报告 open；恢复认证后也不能向已移除连接推送。
            restore(app, session);
            pushBoth();
            verify(session, never()).sendMessage(any());
        }
    }

    @Test
    void connectionWithoutOriginalUserIdIsRejected() throws Exception {
        try (var app = validToken()) {
            WebSocketSession session = session(TOKEN);
            session.getAttributes().remove("userId");
            handler.afterConnectionEstablished(session);
            verify(session).close(CloseStatus.NOT_ACCEPTABLE);
            app.verifyNoInteractions();
            verifyNoInteractions(users);
        }
    }

    @Test
    void validPingAndPushPreserveLargeIdsAsStrings() throws Exception {
        try (var app = validToken()) {
            Map<String, Object> attributes = new HashMap<>();
            assertTrue(handshake(TOKEN, attributes));
            WebSocketSession session = session(TOKEN);
            session.getAttributes().putAll(attributes);
            handler.afterConnectionEstablished(session);
            ping(session);
            handler.pushNewMessage(USER_ID, Long.MAX_VALUE, USER_ID, "hello", Long.MAX_VALUE - 1);
            handler.pushAdminMessage(USER_ID, Long.MAX_VALUE, "support", null);

            var captor = ArgumentCaptor.forClass(TextMessage.class);
            verify(session, times(3)).sendMessage(captor.capture());
            ObjectMapper mapper = new ObjectMapper();
            assertEquals("pong", mapper.readTree(captor.getAllValues().get(0).getPayload()).path("action").asText());
            JsonNode message = mapper.readTree(captor.getAllValues().get(1).getPayload());
            assertEquals("new_message", message.path("action").asText());
            assertStringId(message, "conversationId", Long.MAX_VALUE);
            assertStringId(message, "senderId", USER_ID);
            assertStringId(message, "messageId", Long.MAX_VALUE - 1);
            assertEquals("hello", message.path("content").asText());
            JsonNode admin = mapper.readTree(captor.getAllValues().get(2).getPayload());
            assertEquals("admin_message", admin.path("action").asText());
            assertEquals("官方客服", admin.path("adminName").asText());
            assertStringId(admin, "messageId", Long.MAX_VALUE);
        }
    }

    @ParameterizedTest
    @EnumSource(Cleanup.class)
    void removingOneConnectionDoesNotRemovePeer(Cleanup cleanup) throws Exception {
        try (var app = validToken()) {
            app.when(() -> StpAppUtil.getLoginId("peer-token")).thenReturn(USER_ID);
            WebSocketSession removed = session(TOKEN);
            WebSocketSession peer = session("peer-token");
            handler.afterConnectionEstablished(removed);
            handler.afterConnectionEstablished(peer);
            app.when(() -> StpAppUtil.getLoginId(TOKEN)).thenReturn(null);

            switch (cleanup) {
                case CLOSED -> handler.afterConnectionClosed(removed, CloseStatus.NORMAL);
                case TRANSPORT_ERROR -> handler.handleTransportError(removed, new IOException("transport"));
                case PING -> ping(removed);
                case NEW_MESSAGE -> perform(Operation.NEW_MESSAGE, removed);
                case ADMIN_MESSAGE -> perform(Operation.ADMIN_MESSAGE, removed);
            }
            verify(removed, never()).sendMessage(any());
            if (cleanup != Cleanup.CLOSED)
                verify(removed).close(any(CloseStatus.class));
            clearInvocations(peer);
            restore(app, removed);
            pushBoth();
            verify(removed, never()).sendMessage(any());
            verify(peer, times(2)).sendMessage(any());
            // 重复关闭旧连接也不能误删同一 userId 下的新/其他连接。
            handler.afterConnectionClosed(removed, CloseStatus.NORMAL);
            pushBoth();
            verify(peer, times(4)).sendMessage(any());
        }
    }

    @Test
    void closeAndTransportCleanupDoNotResolveRevokedToken() {
        try (var app = validToken()) {
            WebSocketSession closed = session(TOKEN);
            WebSocketSession failed = session(TOKEN);
            handler.afterConnectionEstablished(closed);
            handler.afterConnectionEstablished(failed);
            app.when(() -> StpAppUtil.getLoginId(TOKEN)).thenReturn(null);
            app.clearInvocations();
            handler.afterConnectionClosed(closed, CloseStatus.NORMAL);
            handler.handleTransportError(failed, new IOException("transport"));
            app.verifyNoInteractions();
            restore(app, closed);
            pushBoth();
            app.verifyNoInteractions();
        }
    }

    @Test
    void failedCloseStillRemovesRevokedConnection() throws Exception {
        try (var app = validToken()) {
            WebSocketSession session = session(TOKEN);
            handler.afterConnectionEstablished(session);
            doThrow(new IOException("close failed")).when(session).close(any(CloseStatus.class));
            app.when(() -> StpAppUtil.getLoginId(TOKEN)).thenReturn(null);
            ping(session);
            restore(app, session);
            pushBoth();
            verify(session, never()).sendMessage(any());
        }
    }

    @Test
    void closedSessionIsRemovedWithoutSending() throws Exception {
        try (var app = validToken()) {
            WebSocketSession session = session(TOKEN);
            handler.afterConnectionEstablished(session);
            when(session.isOpen()).thenReturn(false);
            pushBoth();
            when(session.isOpen()).thenReturn(true);
            pushBoth();
            verify(session, never()).sendMessage(any());
        }
    }

    @Test
    void concurrentPingAndBothPushesUseTheSameSessionLock() throws Exception {
        WebSocketSession session = session(TOKEN);
        try (var app = validToken()) {
            handler.afterConnectionEstablished(session);
        }
        AtomicInteger activeSends = new AtomicInteger();
        doAnswer(invocation -> {
            assertTrue(Thread.holdsLock(session), "所有 sendMessage 必须持有同一 session 锁");
            assertEquals(1, activeSends.incrementAndGet());
            activeSends.decrementAndGet();
            return null;
        }).when(session).sendMessage(any());
        CyclicBarrier start = new CyclicBarrier(3);
        var executor = Executors.newFixedThreadPool(3);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Operation operation : List.of(Operation.PING, Operation.NEW_MESSAGE, Operation.ADMIN_MESSAGE)) {
                futures.add(executor.submit(() -> {
                    // Mockito 静态 mock 是线程局部的，每个发送线程单独绑定。
                    try (var app = validToken()) {
                        start.await(5, TimeUnit.SECONDS);
                        for (int i = 0; i < 20; i++)
                            perform(operation, session);
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures)
                future.get(15, TimeUnit.SECONDS);
            verify(session, times(60)).sendMessage(any());
            verify(session, never()).close(any(CloseStatus.class));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private MockedStatic<StpAppUtil> validToken() {
        MockedStatic<StpAppUtil> app = mockStatic(StpAppUtil.class);
        app.when(() -> StpAppUtil.getLoginId(TOKEN)).thenReturn(USER_ID);
        return app;
    }

    private boolean handshake(String token, Map<String, Object> attributes) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/im");
        if (token != null)
            request.setParameter("token", token);
        return handshake
            .beforeHandshake(new ServletServerHttpRequest(request), mock(ServerHttpResponse.class), handler, attributes);
    }

    private WebSocketSession session(String token) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", USER_ID);
        attributes.put("token", token);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        sessions.add(session);
        return session;
    }

    private void invalidate(InvalidState state, MockedStatic<StpAppUtil> app, WebSocketSession session) {
        switch (state) {
            case REVOKED -> app.when(() -> StpAppUtil.getLoginId(TOKEN)).thenReturn(null);
            case CHANGED_USER -> {
                AppUserDO otherUser = new AppUserDO();
                otherUser.setId(USER_ID + 1);
                otherUser.setAccountStatus(1);
                when(users.selectById(USER_ID + 1)).thenReturn(otherUser);
                app.when(() -> StpAppUtil.getLoginId(TOKEN)).thenReturn(USER_ID + 1);
            }
            case DISABLED -> user.setAccountStatus(2);
            case MISSING_USER -> when(users.selectById(USER_ID)).thenReturn(null);
            case NULL_STATUS -> user.setAccountStatus(null);
            case TOKEN_FAILURE -> app.when(() -> StpAppUtil.getLoginId(TOKEN))
                .thenThrow(new IllegalStateException("token lookup"));
            case ACCOUNT_FAILURE -> when(users.selectById(USER_ID))
                .thenThrow(new IllegalStateException("account lookup"));
            case MISSING_TOKEN -> session.getAttributes().remove("token");
        }
    }

    private void restore(MockedStatic<StpAppUtil> app, WebSocketSession session) {
        app.when(() -> StpAppUtil.getLoginId(TOKEN)).thenReturn(USER_ID);
        doReturn(user).when(users).selectById(USER_ID);
        user.setAccountStatus(1);
        session.getAttributes().put("token", TOKEN);
    }

    private void perform(Operation operation, WebSocketSession session) throws Exception {
        switch (operation) {
            case CONNECT -> handler.afterConnectionEstablished(session);
            case PING -> ping(session);
            case NEW_MESSAGE -> handler.pushNewMessage(USER_ID, 11L, 22L, "private message", 33L);
            case ADMIN_MESSAGE -> handler.pushAdminMessage(USER_ID, 44L, "private support", "support");
        }
    }

    private void ping(WebSocketSession session) throws Exception {
        handler.handleMessage(session, new TextMessage("{\"action\":\"ping\"}"));
    }

    private void pushBoth() {
        handler.pushNewMessage(USER_ID, 11L, 22L, "private message", 33L);
        handler.pushAdminMessage(USER_ID, 44L, "private support", "support");
    }

    private void assertStringId(JsonNode node, String field, long expected) {
        assertTrue(node.path(field).isTextual(), field + " 必须是字符串");
        assertEquals(Long.toString(expected), node.path(field).asText());
    }

    enum InvalidState {
        REVOKED, CHANGED_USER, DISABLED, MISSING_USER, NULL_STATUS, TOKEN_FAILURE, ACCOUNT_FAILURE, MISSING_TOKEN
    }

    enum Operation {
        CONNECT, PING, NEW_MESSAGE, ADMIN_MESSAGE
    }

    enum Cleanup {
        CLOSED, TRANSPORT_ERROR, PING, NEW_MESSAGE, ADMIN_MESSAGE
    }
}
