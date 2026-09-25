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
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * 寻人档案动作请求参数（续期、撤回、删除等仅需说明原因的轻量动作）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "寻人档案动作请求参数")
public class CaseActionReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 原因说明
     */
    @Schema(description = "原因说明", example = "家属已自行找到，撤回发布")
    @Length(max = 500, message = "原因说明长度不能超过 {max} 个字符")
    private String reason;
}
