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

/**
 * AI 文本类响应(周报/点评/拆解共用信封字段)
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Data
@Schema(description = "AI 文本响应")
public class AiTextResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "文本内容;拆解接口为 JSON 数组字符串")
    private String text;

    @Schema(description = "是否测试模式(服务端未配置 Key)")
    private Boolean mockMode;
}
