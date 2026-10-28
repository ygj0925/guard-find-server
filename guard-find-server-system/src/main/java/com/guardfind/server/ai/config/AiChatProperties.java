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

package com.guardfind.server.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 对话配置属性(guardfind.ai.*)
 *
 * <p>
 * Key 只放在服务端配置,永不下发端上。{@code apiKey} 为空时进入"测试模式":
 * 不调用供应商,由 {@code LlmClient} 返回明确标注的兜底回复,开发环境无需真实 Key。
 * </p>
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Data
@Component
@ConfigurationProperties(prefix = "guardfind.ai")
public class AiChatProperties {

    /**
     * OpenAI 兼容接口基址(如 https://api.openai.com/v1 或自建代理)
     */
    private String apiBase = "";

    /**
     * 供应商 API Key;为空 = 测试模式
     */
    private String apiKey = "";

    /**
     * 模型标识(如 gpt-4o-mini / glm-4-flash)
     */
    private String model = "glm-4-flash";

    /**
     * 上行/下行单条内容长度上限(字符)
     */
    private int maxContentLength = 4000;

    /**
     * 携带的历史轮数(上下文窗口裁剪,防止账单式膨胀)
     */
    private int historyTurns = 20;

    /**
     * 请求超时(毫秒)
     */
    private int timeoutMs = 60000;
}
