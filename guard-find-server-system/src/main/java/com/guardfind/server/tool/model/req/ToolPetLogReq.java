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
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 宠物成长日志请求
 */
@Data
@Schema(description = "宠物成长日志请求")
public class ToolPetLogReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "记录类型(weight/vaccine/medical/milestone/daily)", example = "weight")
    @NotBlank(message = "记录类型不能为空")
    @Pattern(regexp = "^(weight|vaccine|medical|milestone|daily)$", message = "记录类型取值不合法")
    private String logType;

    @Schema(description = "记录内容", example = "体重 4.2kg,比上月增 0.3kg")
    @NotBlank(message = "记录内容不能为空")
    @Length(max = 1000, message = "内容不能超过 {max} 个字符")
    private String content;

    @Schema(description = "数值(体重 kg 等)")
    private BigDecimal value;

    @Schema(description = "单位", example = "kg")
    @Length(max = 10, message = "单位不能超过 {max} 个字符")
    private String unit;

    @Schema(description = "记录日期")
    @NotNull(message = "记录日期不能为空")
    private LocalDate logDate;
}
