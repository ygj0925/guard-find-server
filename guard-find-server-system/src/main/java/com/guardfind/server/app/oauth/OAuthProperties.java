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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 三方 OAuth 配置(guardfind.oauth.*)。
 * AppID/AppSecret 从环境变量注入;留空 = 该三方按钮置灰("即将开放")。
 */
@Data
@Component
@ConfigurationProperties(prefix = "guardfind.oauth")
public class OAuthProperties {

    private ProviderConfig wechat = new ProviderConfig();
    private ProviderConfig qq = new ProviderConfig();
    private ProviderConfig douyin = new ProviderConfig();

    @Data
    public static class ProviderConfig {
        private String appId = "";
        private String appSecret = "";
    }
}
