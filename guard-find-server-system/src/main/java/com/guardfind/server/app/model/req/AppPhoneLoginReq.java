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
import org.hibernate.validator.constraints.Length;
import top.continew.starter.validation.constraints.Mobile;

import java.io.Serial;

/**
 * C 端手机号验证码登录请求参数
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "C 端手机号验证码登录请求参数")
public class AppPhoneLoginReq extends AppLoginReq {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 手机号
     */
    @Schema(description = "手机号", example = "13800001234")
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
     * 行为（图形）验证码标识（连续 3 次验证码错误后必填）
     */
    @Schema(description = "行为（图形）验证码标识（连续 3 次验证码错误后必填）", example = "090b9a2c-1691-4fca-99db-e4ed0cff362f")
    private String captchaId;

    /**
     * 行为（图形）验证码值（连续 3 次验证码错误后必填）
     */
    @Schema(description = "行为（图形）验证码值（连续 3 次验证码错误后必填）", example = "ABCD")
    private String captchaValue;
}
