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
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 创建/修改宠物请求
 */
@Data
@Schema(description = "宠物档案请求")
public class ToolPetReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "宠物名", example = "毛毛")
    @NotBlank(message = "宠物名不能为空")
    @Length(max = 30, message = "宠物名不能超过 {max} 个字符")
    private String name;

    @Schema(description = "物种(cat/dog/other)", example = "cat")
    @NotBlank(message = "物种不能为空")
    @Pattern(regexp = "^(cat|dog|other)$", message = "物种取值不合法")
    private String species;

    @Schema(description = "品种", example = "英短蓝猫")
    @Length(max = 50, message = "品种不能超过 {max} 个字符")
    private String breed;

    @Schema(description = "性别(1 公 / 2 母)")
    private Integer gender;

    @Schema(description = "生日")
    private LocalDate birthday;

    @Schema(description = "体重(kg)")
    private BigDecimal weightKg;

    @Schema(description = "备注")
    @Length(max = 500, message = "备注不能超过 {max} 个字符")
    private String notes;
}
