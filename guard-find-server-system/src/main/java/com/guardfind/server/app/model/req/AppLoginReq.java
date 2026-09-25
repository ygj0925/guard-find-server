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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import com.guardfind.server.auth.enums.AuthTypeEnum;

import java.io.Serial;
import java.io.Serializable;

/**
 * C 端登录请求参数基类
 *
 * <p>
 * 与后台 {@code auth/model/req/LoginReq.java} 一致，按 {@code authType} 做 Jackson 多态反序列化；
 * 一期 C 端仅支持 PHONE（短信验证码，未注册自动建档）与 ACCOUNT（账号密码）两种方式。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "authType", visible = true)
@JsonSubTypes({@JsonSubTypes.Type(value = AppAccountLoginReq.class, name = "ACCOUNT"),
    @JsonSubTypes.Type(value = AppPhoneLoginReq.class, name = "PHONE")})
@Schema(description = "C 端登录请求参数基类")
public class AppLoginReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 认证类型（PHONE：手机号验证码；ACCOUNT：账号密码）
     */
    @Schema(description = "认证类型（PHONE：手机号验证码；ACCOUNT：账号密码）", example = "PHONE")
    private AuthTypeEnum authType;

    /**
     * 设备标识（可选；缺省时从 X-Device-Id 请求头读取）
     */
    @Schema(description = "设备标识（可选，用于多设备登录与设备登记）", example = "f40a2b9c-2d51-4a72-9f3e-8a1c7d5b0e11")
    private String deviceId;

    /**
     * 设备名称（可选）
     */
    @Schema(description = "设备名称（可选）", example = "iPhone 15 Pro")
    private String deviceName;

    /**
     * 平台（可选，IOS/ANDROID/HARMONY/WEB）
     */
    @Schema(description = "平台（可选，IOS/ANDROID/HARMONY/WEB）", example = "IOS")
    private String platform;

    /**
     * App 版本（可选）
     */
    @Schema(description = "App 版本（可选）", example = "1.0.0")
    private String appVersion;

    /**
     * 系统版本（可选）
     */
    @Schema(description = "系统版本（可选）", example = "iOS 18.1")
    private String osVersion;
}
