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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * KR 创建/修改请求
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@Schema(description = "KR 请求")
public class ToolKeyResultReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "KR 描述", example = "跑步 100 公里")
    @NotBlank(message = "KR 描述不能为空")
    @Length(max = 100, message = "KR 不能超过 {max} 个字符")
    private String title;

    @Schema(description = "目标值", example = "100")
    @NotNull(message = "目标值不能为空")
    @DecimalMin(value = "0.01", message = "目标值必须大于 0")
    @Digits(integer = 10, fraction = 2, message = "目标值最多 10 位整数、2 位小数")
    private BigDecimal targetValue;

    @Schema(description = "单位", example = "公里")
    @Length(max = 10, message = "单位不能超过 {max} 个字符")
    private String unit;
}
