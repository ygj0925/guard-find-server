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

package com.guardfind.server.app;

import cn.hutool.http.HttpUtil;
import com.guardfind.server.app.oauth.OAuthProperties;
import com.guardfind.server.app.oauth.WechatMiniappService;
import com.guardfind.server.support.ContainerlessTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import top.continew.starter.core.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * 微信小程序登录单测:未配置拒绝、errcode 处理、openid 解析。
 */
@DisplayName("微信小程序登录")
class WechatMiniappServiceTest extends ContainerlessTestBase {

    private OAuthProperties properties;
    private WechatMiniappService service;

    @BeforeEach
    void setUp() {
        properties = new OAuthProperties();
        service = new WechatMiniappService(properties);
    }

    private void configure() {
        properties.getWechat().setAppId("wx123");
        properties.getWechat().setAppSecret("secret");
    }

    @Test
    @DisplayName("未配置 AppID/AppSecret 时直接拒绝")
    void rejectsWhenNotConfigured() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.jsCode2Session("code"));
        assertTrue(ex.getMessage().contains("暂未开放"));
    }

    @Test
    @DisplayName("微信侧返回 errcode 非零时拒绝")
    void rejectsWechatError() {
        configure();
        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.get(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers
                .anyInt())).thenReturn("{\"errcode\":40029,\"errmsg\":\"invalid code\"}");

            BusinessException ex = assertThrows(BusinessException.class, () -> service.jsCode2Session("bad-code"));
            assertTrue(ex.getMessage().contains("微信登录失败"));
        }
    }

    @Test
    @DisplayName("正常响应解析出 openid/unionid")
    void parsesOpenid() {
        configure();
        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.get(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers
                .anyInt())).thenReturn("{\"openid\":\"oX1234567890\",\"unionid\":\"uX123\",\"session_key\":\"sk==\"}");

            WechatMiniappService.WxSession session = service.jsCode2Session("good-code");
            assertEquals("oX1234567890", session.getOpenid());
            assertEquals("uX123", session.getUnionid());
            assertEquals("sk==", session.getSessionKey());
        }
    }

    @Test
    @DisplayName("响应缺 openid 时拒绝")
    void rejectsMissingOpenid() {
        configure();
        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.get(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers
                .anyInt())).thenReturn("{\"session_key\":\"sk==\"}");

            assertThrows(BusinessException.class, () -> service.jsCode2Session("code"));
        }
    }

    @Test
    @DisplayName("HTTP 异常时拒绝且不抛底层异常")
    void rejectsHttpFailure() {
        configure();
        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.get(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers
                .anyInt())).thenThrow(new RuntimeException("connection refused"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.jsCode2Session("code"));
            assertTrue(ex.getMessage().contains("微信登录失败"));
        }
    }
}
