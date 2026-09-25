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

package com.guardfind.server.app.model.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * C 端登录响应参数
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "C 端登录响应参数")
public class AppLoginResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 令牌
     */
    @Schema(description = "令牌", example = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.xxx")
    private String token;

    /**
     * 令牌名称（App 侧以该名称作为请求头携带令牌）
     */
    @Schema(description = "令牌名称", example = "Authorization")
    private String tokenName;

    /**
     * 令牌有效期（单位：秒，取自 sys_client 的 APP 客户端配置）
     */
    @Schema(description = "令牌有效期（单位：秒）", example = "2592000")
    private Long expiresIn;

    /**
     * 是否新注册用户（true 时 App 展示非阻断的资料引导）
     */
    @Schema(description = "是否新注册用户", example = "true")
    @JsonProperty("isNewUser")
    private Boolean isNewUser;

    /**
     * 本次登录是否撤销了处于冷静期的注销申请
     */
    @Schema(description = "本次登录是否撤销了处于冷静期的注销申请", example = "false")
    private Boolean deactivationCancelled;

    /**
     * 用户信息
     */
    @Schema(description = "用户信息")
    private AppUserResp user;
}
