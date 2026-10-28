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
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 待办新增/修改请求
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Data
@Schema(description = "待办请求参数")
public class ToolTodoReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "待办内容", example = "买生日礼物")
    @NotBlank(message = "待办内容不能为空")
    @Length(max = 100, message = "待办内容长度不能超过 {max} 个字符")
    private String title;

    @Schema(description = "是否完成", example = "false")
    private Boolean done;

    @Schema(description = "截止日期", example = "2026-11-05")
    private LocalDate dueDate;
}
