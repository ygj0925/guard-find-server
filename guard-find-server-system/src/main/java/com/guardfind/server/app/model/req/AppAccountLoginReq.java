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
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * C 端账号密码登录请求参数
 *
 * <p>
 * 说明：一期 C 端账号即已绑定的手机号（gf_app_user 无独立用户名列，手机号为唯一业务账号标识）。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "C 端账号密码登录请求参数")
public class AppAccountLoginReq extends AppLoginReq {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 登录账号（一期为已绑定手机号）
     */
    @Schema(description = "登录账号（一期为已绑定手机号）", example = "13800001234")
    @NotBlank(message = "账号不能为空")
    private String account;

    /**
     * 密码
     */
    @Schema(description = "密码", example = "Guard123456")
    @NotBlank(message = "密码不能为空")
    private String password;
}
