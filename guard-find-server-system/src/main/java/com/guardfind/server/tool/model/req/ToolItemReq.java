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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * 物品收纳新增/修改请求
 *
 * @author guardfind
 * @since 2026/11/06
 */
@Data
@Schema(description = "物品收纳请求参数")
public class ToolItemReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "物品名称", example = "螺丝刀")
    @NotBlank(message = "物品名称不能为空")
    @Length(max = 100, message = "物品名称长度不能超过 {max} 个字符")
    private String name;

    @Schema(description = "存放位置", example = "阳台工具箱")
    @Length(max = 100, message = "位置长度不能超过 {max} 个字符")
    private String location;

    @Schema(description = "数量", example = "1")
    @Min(value = 0, message = "数量不能为负数")
    @Max(value = 999999, message = "数量不能超过 {max}")
    private Integer quantity;

    @Schema(description = "备注", example = "带磁性批头")
    @Length(max = 500, message = "备注长度不能超过 {max} 个字符")
    private String note;

    @Schema(description = "是否归档", example = "false")
    private Boolean archived;

    @Schema(description = "修改时必传，取自上次读取的 version", example = "1")
    private Integer version;
}
