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

package com.guardfind.server.ai.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 会话响应
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Data
@Schema(description = "AI 会话响应")
public class AiConversationResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "会话 ID")
    private Long id;

    @Schema(description = "会话标题")
    private String title;

    @Schema(description = "模型标识")
    private String model;

    @Schema(description = "更新时间(最近活跃)")
    private LocalDateTime updateTime;

    @Schema(description = "是否测试模式(服务端未配置 LLM Key)")
    private Boolean mockMode;
}
