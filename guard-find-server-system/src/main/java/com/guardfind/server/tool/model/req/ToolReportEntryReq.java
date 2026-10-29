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

package com.guardfind.server.tool.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * 保存月报请求(填报人本人)
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@Schema(description = "保存月报请求")
public class ToolReportEntryReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "汇报正文")
    @NotBlank(message = "汇报正文不能为空")
    @Length(max = 5000, message = "正文不能超过 {max} 个字符")
    private String content;

    @Schema(description = "true=提交(false=存草稿);提交后全成员可读", example = "true")
    @NotNull(message = "请指定保存类型")
    private Boolean submit;
}
