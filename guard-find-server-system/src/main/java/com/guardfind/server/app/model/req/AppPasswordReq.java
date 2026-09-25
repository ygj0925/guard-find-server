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
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import com.guardfind.server.common.constant.RegexConstants;
import top.continew.starter.validation.constraints.Mobile;

import java.io.Serial;
import java.io.Serializable;

/**
 * C 端设置/修改密码请求参数
 *
 * <p>
 * 已设置过密码的账号必须携带 oldPassword；未设置过密码的账号（仅验证码注册）通过 phone + code 短信核验后设置密码。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "C 端设置/修改密码请求参数")
public class AppPasswordReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前密码（已设置密码时必填）
     */
    @Schema(description = "当前密码（已设置密码时必填）", example = "Guard123456")
    private String oldPassword;

    /**
     * 新密码
     */
    @Schema(description = "新密码", example = "Guard654321")
    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = RegexConstants.PASSWORD, message = "密码长度为 8-32 个字符，且至少包含字母和数字")
    private String newPassword;

    /**
     * 手机号（未设置过密码时必填，用于短信核验）
     */
    @Schema(description = "手机号（未设置过密码时必填，用于短信核验）", example = "13800001234")
    @Mobile
    private String phone;

    /**
     * 短信验证码（未设置过密码时必填）
     */
    @Schema(description = "短信验证码（未设置过密码时必填）", example = "123456")
    private String code;
}
