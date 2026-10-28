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
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * KR 进度更新请求
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@Schema(description = "KR 进度更新请求")
public class ToolKrProgressReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "当前值", example = "42.5")
    @NotNull(message = "当前值不能为空")
    @DecimalMin(value = "0", message = "当前值不能为负")
    @Digits(integer = 10, fraction = 2, message = "当前值最多 10 位整数、2 位小数")
    private BigDecimal currentValue;
}
