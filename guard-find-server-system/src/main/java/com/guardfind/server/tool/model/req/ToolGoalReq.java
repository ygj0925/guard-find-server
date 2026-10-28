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
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * 目标创建/修改请求
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@Schema(description = "目标请求")
public class ToolGoalReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "目标(O)描述", example = "Q4 养成规律运动")
    @NotBlank(message = "目标描述不能为空")
    @Length(max = 100, message = "目标不能超过 {max} 个字符")
    private String title;

    @Schema(description = "周期(2026Q4 或 2026)", example = "2026Q4")
    @NotBlank(message = "周期不能为空")
    @Pattern(regexp = "^\\d{4}(Q[1-4])?$", message = "周期格式应为 2026 或 2026Q4")
    private String period;
}
