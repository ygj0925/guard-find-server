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

package com.guardfind.server.findcase.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * 寻人档案别名请求参数（小名、乳名、口音叫法等，检索命中的关键补充信息）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "寻人档案别名请求参数")
public class CaseAliasReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 别名
     */
    @Schema(description = "别名", example = "小虎")
    @NotBlank(message = "别名不能为空")
    @Length(max = 64, message = "别名长度不能超过 {max} 个字符")
    private String name;

    /**
     * 类别（NICKNAME/ACCENT/OTHER）
     */
    @Schema(description = "类别（NICKNAME/ACCENT/OTHER）", example = "NICKNAME")
    @Length(max = 16, message = "别名类别长度不能超过 {max} 个字符")
    private String kind;
}
