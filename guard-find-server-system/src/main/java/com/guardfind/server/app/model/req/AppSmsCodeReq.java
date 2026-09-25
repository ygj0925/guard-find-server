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
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.guardfind.server.app.enums.AppSmsSceneEnum;
import top.continew.starter.validation.constraints.Mobile;

import java.io.Serial;
import java.io.Serializable;

/**
 * C 端短信验证码请求参数
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "C 端短信验证码请求参数")
public class AppSmsCodeReq implements Serializable {

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
     * 场景（LOGIN：登录；RESET：重置密码；BIND：绑定手机号）
     */
    @Schema(description = "场景（LOGIN：登录；RESET：重置密码；BIND：绑定手机号）", example = "LOGIN")
    @NotNull(message = "验证码场景不能为空")
    private AppSmsSceneEnum scene;
}
