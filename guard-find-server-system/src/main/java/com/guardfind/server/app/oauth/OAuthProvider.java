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

package com.guardfind.server.app.oauth;

/**
 * 三方 OAuth 提供者接口(微信/QQ/抖音统一抽象)。
 * 接入步骤:配置 AppID/AppSecret → 实现 or 启用对应 Provider → 前端按钮自动激活。
 *
 * @author guardfind
 * @since 2026/10/29
 */
public interface OAuthProvider {

    /** 提供者标识(wechat/qq/douyin)。 */
    String provider();

    /** 生成三方授权跳转 URL(用户在浏览器/SDK 中打开)。 */
    String authorizationUrl(String state);

    /** 用授权码换取 access_token。 */
    String exchangeToken(String code);

    /** 用 access_token 获取用户信息。 */
    OAuthUserInfo fetchUserInfo(String accessToken);

    /** 是否已配置(有 AppID 和 AppSecret)。 */
    boolean isConfigured();
}
