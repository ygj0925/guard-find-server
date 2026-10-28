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

package com.guardfind.server.ai.support;

import java.util.List;

/**
 * LLM 客户端抽象(服务端代理转发,Key 不落端上)
 *
 * @author guardfind
 * @since 2026/10/28
 */
public interface LlmClient {

    /**
     * 发起一轮对话补全
     *
     * @param messages 携带 system 提示词的完整上下文(按时间升序)
     * @return 助手回复文本
     */
    String chat(List<LlmMessage> messages);
}
