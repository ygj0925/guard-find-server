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

package com.guardfind.server.lead.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * 线索状态处置请求参数
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "线索状态处置请求参数")
public class LeadStatusReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 目标状态（ACCEPTED/REJECTED/CONTACTED）
     */
    @Schema(description = "目标状态（ACCEPTED/REJECTED/CONTACTED）", example = "ACCEPTED")
    @NotBlank(message = "线索状态不能为空")
    private String status;

    /**
     * 处置说明
     */
    @Schema(description = "处置说明", example = "已报警并转交属地志愿者核实")
    @Length(max = 500, message = "处置说明长度不能超过 {max} 个字符")
    private String remark;
}
