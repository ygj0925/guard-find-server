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
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * AA 分账请求
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Data
@Schema(description = "AA 分账请求参数")
public class ToolSplitReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "分账事由", example = "周五聚餐")
    @Length(max = 50, message = "事由长度不能超过 {max} 个字符")
    private String title;

    @Schema(description = "总金额(元)", example = "312.60")
    @NotNull(message = "总金额不能为空")
    @DecimalMin(value = "0.01", message = "总金额必须大于 0")
    @Digits(integer = 10, fraction = 2, message = "金额最多 10 位整数、2 位小数")
    private BigDecimal totalAmount;

    @Schema(description = "参与人数", example = "5")
    @NotNull(message = "参与人数不能为空")
    @Min(value = 1, message = "至少 1 人")
    @Max(value = 99, message = "人数最多 99 人")
    private Integer peopleCount;

    @Schema(description = "分账日期", example = "2026-10-28")
    @NotNull(message = "分账日期不能为空")
    private LocalDate splitDate;

    @Schema(description = "备注")
    @Length(max = 200, message = "备注长度不能超过 {max} 个字符")
    private String remark;
}
