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

package com.guardfind.server.ledger.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创建家庭组请求
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@Schema(description = "创建家庭组请求")
public class LedgerGroupReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "组名", example = "我们的家")
    @NotBlank(message = "组名不能为空")
    @Length(min = 1, max = 50, message = "组名长度必须在 {min}-{max} 个字符之间")
    private String name;
}
