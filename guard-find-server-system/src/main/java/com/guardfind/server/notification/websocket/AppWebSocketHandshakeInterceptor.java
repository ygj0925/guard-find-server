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
import com.guardfind.server.app.util.StpAppUtil;
import com.guardfind.server.notification.support.NotificationConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * C 端实时通道握手拦截器
 *
 * <p>
 * 模板的 {@code /websocket} 握手以后台 Sa-Token 域校验令牌（见 {@code WebSocketClientServiceImpl#getClientId} 中
 * {@code StpUtil.getLoginIdByToken(token)}），C 端 {@code loginType=app} 的令牌在该域中不存在，
 * 因此本端点自行以 {@link StpAppUtil#getLoginId(String)} 鉴权：<b>令牌无效即拒绝握手</b>（响应 401，
 * 会话不会进入 {@code AppWebSocketHandler}，注册表不落任何记录）。
 * </p>
 *
 * <p>
 * <b>注意：本类不得声明为 Spring Bean</b>——模板
 * {@code WebSocketAutoConfiguration#handshakeInterceptor} 带
 * {@code @ConditionalOnMissingBean(HandshakeInterceptor.class)}，
 * 注册成 Bean 会顶掉后台 {@code /websocket} 的握手拦截器。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
public class AppWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    /**
     * Bearer 前缀（长度 7，含尾随空格）
     */
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = this.resolveToken(request);
        Long userId = StpAppUtil.getLoginId(token);
        if (userId == null) {
            // 令牌无效/过期（含后台 token 冒用）：拒绝握手，不登记会话
            log.warn("C 端实时通道握手被拒绝：令牌无效或已过期");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(NotificationConstants.WS_USER_ID_ATTR, userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        if (exception != null) {
            log.warn("C 端实时通道握手异常：{}", exception.getMessage());
        }
    }

    /**
     * 解析令牌：优先取 {@code ?token=}（浏览器与 RN 的 WebSocket 难以自定义请求头），回退 {@code Authorization} 头
     *
     * @param request 请求
     * @return 令牌，未携带时为 null
     */
    String resolveToken(ServerHttpRequest request) {
        String token = UriComponentsBuilder.fromUri(request.getURI())
            .build()
            .getQueryParams()
            .getFirst(NotificationConstants.WS_TOKEN_PARAM);
        if (StrUtil.isNotBlank(token)) {
            return StrUtil.trim(token);
        }
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StrUtil.isBlank(header)) {
            return null;
        }
        header = StrUtil.trim(header);
        return StrUtil.startWithIgnoreCase(header, BEARER_PREFIX)
            ? StrUtil.trim(StrUtil.removePrefixIgnoreCase(header, BEARER_PREFIX))
            : header;
    }
}
