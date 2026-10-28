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
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 任务创建/修改请求
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@Schema(description = "任务分派请求")
public class ToolTaskReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "所属家庭组 ID")
    @NotNull(message = "家庭组不能为空")
    private Long groupId;

    @Schema(description = "负责人用户 ID(须为组成员)")
    @NotNull(message = "负责人不能为空")
    private Long assigneeUserId;

    @Schema(description = "任务标题")
    @NotBlank(message = "任务标题不能为空")
    @Length(max = 100, message = "标题不能超过 {max} 个字符")
    private String title;

    @Schema(description = "详情")
    @Length(max = 500, message = "详情不能超过 {max} 个字符")
    private String detail;

    @Schema(description = "截止日期", example = "2026-11-05")
    private LocalDate dueDate;

    @Schema(description = "优先级(1 高 / 2 中 / 3 低)", example = "2")
    @Pattern(regexp = "^[123]$", message = "优先级取值不合法")
    private String priority;
}
