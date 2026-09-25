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

package com.guardfind.server.lead.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * 线索回复请求参数
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "线索回复请求参数")
public class LeadReplyReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 回复内容（1–500 字）
     */
    @Schema(description = "回复内容（1–500 字）", example = "请提供当时的照片，我们已联系属地派出所")
    @NotBlank(message = "回复内容不能为空")
    @Length(min = 1, max = 500, message = "回复内容长度必须在 {min}-{max} 个字符之间")
    private String content;
}
