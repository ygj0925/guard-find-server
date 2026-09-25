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
import org.hibernate.validator.constraints.Length;
import com.guardfind.server.common.constant.RegexConstants;
import top.continew.starter.validation.constraints.Mobile;

import java.io.Serial;
import java.io.Serializable;

/**
 * C 端重置密码请求参数（公开接口，凭短信验证码重置）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "C 端重置密码请求参数")
public class AppResetPasswordReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 手机号（须为已注册账号的绑定手机号）
     */
    @Schema(description = "手机号（须为已注册账号的绑定手机号）", example = "13800001234")
    @NotBlank(message = "手机号不能为空")
    @Mobile
    private String phone;

    /**
     * 短信验证码
     */
    @Schema(description = "短信验证码", example = "123456")
    @NotBlank(message = "验证码不能为空")
    @Length(min = 6, max = 6, message = "验证码无效")
    private String code;

    /**
     * 新密码
     */
    @Schema(description = "新密码", example = "Guard654321")
    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = RegexConstants.PASSWORD, message = "密码长度为 8-32 个字符，且至少包含字母和数字")
    private String newPassword;
}
