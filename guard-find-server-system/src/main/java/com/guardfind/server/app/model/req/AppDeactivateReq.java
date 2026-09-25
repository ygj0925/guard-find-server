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

package com.guardfind.server.app.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * C 端账号注销申请请求参数
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "C 端账号注销申请请求参数")
public class AppDeactivateReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 注销原因
     */
    @Schema(description = "注销原因", example = "不再使用本应用")
    @NotBlank(message = "注销原因不能为空")
    @Length(max = 200, message = "注销原因长度不能超过 200 个字符")
    private String reason;
}
