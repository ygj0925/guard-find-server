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

package com.guardfind.server.collect.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * 候选驳回请求参数
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "候选驳回请求参数")
public class CandidateRejectReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 理由码（字典 gf_reject_reason）
     */
    @Schema(description = "理由码", example = "INCOMPLETE")
    @NotBlank(message = "驳回理由码不能为空")
    @Length(max = 32, message = "理由码长度不能超过 {max} 个字符")
    private String reasonCode;

    /**
     * 说明
     */
    @Schema(description = "说明")
    @Length(max = 500, message = "说明长度不能超过 {max} 个字符")
    private String remark;
}
