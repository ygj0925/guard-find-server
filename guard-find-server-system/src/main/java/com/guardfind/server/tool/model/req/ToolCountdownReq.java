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
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 倒数日新增/修改请求
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Data
@Schema(description = "倒数日请求参数")
public class ToolCountdownReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "事项名称", example = "考研初试")
    @Length(min = 1, max = 50, message = "事项名称长度必须在 {min}-{max} 个字符之间")
    private String title;

    @Schema(description = "目标日期", example = "2026-12-20")
    @NotNull(message = "目标日期不能为空")
    private LocalDate targetDate;

    @Schema(description = "备注")
    @Length(max = 200, message = "备注长度不能超过 {max} 个字符")
    private String remark;
}
