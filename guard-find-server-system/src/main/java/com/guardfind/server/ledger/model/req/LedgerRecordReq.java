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

package com.guardfind.server.ledger.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 记账记录新增/修改请求参数
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Data
@Schema(description = "记账记录请求参数")
public class LedgerRecordReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "类型（1：支出；2：收入）", example = "1")
    @NotNull(message = "收支类型不能为空")
    private Integer type;

    @Schema(description = "金额（元，正数，最多两位小数）", example = "35.50")
    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于 0")
    @Digits(integer = 10, fraction = 2, message = "金额最多 10 位整数、2 位小数")
    private BigDecimal amount;

    @Schema(description = "分类码", example = "food")
    @Pattern(regexp = "^[a-z]{1,20}$", message = "分类码不合法")
    private String category;

    @Schema(description = "记账日期", example = "2026-10-28")
    @NotNull(message = "记账日期不能为空")
    private LocalDate recordDate;

    @Schema(description = "备注")
    @Length(max = 200, message = "备注长度不能超过 {max} 个字符")
    private String remark;
}
