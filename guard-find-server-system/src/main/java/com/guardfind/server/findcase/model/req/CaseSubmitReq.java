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
 * 提交审核请求参数（可整体省略，字段均可为空）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "提交审核请求参数")
public class CaseSubmitReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 命中疑似重复档案时是否强制提交
     */
    @Schema(description = "命中疑似重复档案时是否强制提交", example = "false")
    private Boolean forceSubmit;

    /**
     * 提交说明
     */
    @Schema(description = "提交说明", example = "补充了最新照片")
    @Length(max = 500, message = "提交说明长度不能超过 {max} 个字符")
    private String remark;
}
