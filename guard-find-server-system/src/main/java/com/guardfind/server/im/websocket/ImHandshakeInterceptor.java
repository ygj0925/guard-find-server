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

import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.app.util.StpAppUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * IM WebSocket 握手拦截器:验证 Sa-Token 和账号状态，将 userId 与原始 token 绑定到 session。
 * 客户端连接 URL: ws://host/ws/im?token={saToken}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImHandshakeInterceptor implements HandshakeInterceptor {

    private final AppUserMapper appUserMapper;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String token = servletRequest.getServletRequest().getParameter("token");
            if (token != null && !token.isBlank()) {
                try {
                    Long userId = StpAppUtil.getLoginId(token);
                    AppUserDO user = userId == null ? null : appUserMapper.selectById(userId);
                    if (user != null && Integer.valueOf(1).equals(user.getAccountStatus())) {
                        attributes.put("userId", userId);
                        attributes.put("token", token);
                        return true;
                    }
                } catch (Exception e) {
                    log.debug("[ws:im] token 或账号验证失败");
                }
            }
        }
        log.warn("[ws:im] WebSocket 握手未通过鉴权(app 登录域)");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }
}
