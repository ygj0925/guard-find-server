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

package com.guardfind.server.notification.config;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardfind.server.notification.service.NotificationRealtimeService;
import com.guardfind.server.notification.websocket.AppWebSocketHandler;
import com.guardfind.server.notification.websocket.AppWebSocketHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import top.continew.starter.messaging.websocket.autoconfigure.WebSocketProperties;

import java.util.List;

/**
 * C 端实时通道配置（契约 §5 {@code /app/websocket?token=}）
 *
 * <p>
 * 与模板的后台 {@code /websocket} 并存：{@code @EnableWebSocket} 在两侧重复声明是安全的
 * （Spring 的 {@code DelegatingWebSocketConfiguration} 会收集容器内<b>全部</b> {@link WebSocketConfigurer}），
 * 但处理器与握手拦截器必须以<b>普通对象</b>方式传入，不能注册成 Bean，否则会命中模板
 * {@code WebSocketAutoConfiguration} 上 {@code @ConditionalOnMissingBean} 的条件，把后台通道顶掉。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@EnableWebSocket
@Configuration
@RequiredArgsConstructor
public class AppWebSocketConfiguration implements WebSocketConfigurer {

    /**
     * C 端实时通道路径（契约 §0 写的是 {@code WS_BASE_URL + /websocket}，因模板握手只认后台登录域令牌，
     * C 端独立到 {@code /app/websocket}，App 侧同步改该常量）
     */
    public static final String APP_WEBSOCKET_PATH = "/app/websocket";

    private final NotificationRealtimeService notificationRealtimeService;

    private final ObjectMapper objectMapper;

    /**
     * 模板 WebSocket 配置（仅在 {@code continew-starter.messaging.websocket.enabled=true} 时存在，故用 ObjectProvider）
     */
    private final ObjectProvider<WebSocketProperties> webSocketPropertiesProvider;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new AppWebSocketHandler(notificationRealtimeService, objectMapper), APP_WEBSOCKET_PATH)
            .addInterceptors(new AppWebSocketHandshakeInterceptor())
            .setAllowedOriginPatterns(this.resolveAllowedOriginPatterns());
    }

    /**
     * 复用模板已配置的跨域白名单（{@code continew-starter.messaging.websocket.allowed-origins}），
     * 未配置/模板通道关闭时回退 {@code *}。
     *
     * <p>
     * 回退为 {@code *} 是可接受的：握手凭 {@code app} 域 token 鉴权、不使用 Cookie，
     * 放开来源不会造成身份伪造；如需收紧，运维只需配置上述 allowed-origins 即可对两条通道同时生效。
     * </p>
     *
     * @return 允许的来源模式
     */
    String[] resolveAllowedOriginPatterns() {
        WebSocketProperties properties = webSocketPropertiesProvider.getIfAvailable();
        List<String> allowedOrigins = properties == null ? null : properties.getAllowedOrigins();
        if (CollUtil.isEmpty(allowedOrigins)) {
            return new String[] {"*"};
        }
        return allowedOrigins.stream().filter(StrUtil::isNotBlank).map(String::trim).toArray(String[]::new);
    }
}
