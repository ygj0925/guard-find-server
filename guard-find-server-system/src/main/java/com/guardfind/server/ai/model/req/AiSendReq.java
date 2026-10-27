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

package com.guardfind.server.ai.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 发送消息请求
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Data
@Schema(description = "AI 发送消息请求")
public class AiSendReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "消息内容", example = "帮我把这句话翻译成英文:今天天气不错")
    @NotBlank(message = "消息内容不能为空")
    @Length(max = 4000, message = "消息长度不能超过 {max} 个字符")
    private String content;
}
