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

package com.guardfind.server.app.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import top.continew.starter.core.enums.BaseEnum;

/**
 * C 端短信验证码场景
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Getter
@RequiredArgsConstructor
public enum AppSmsSceneEnum implements BaseEnum<String> {

    /**
     * 登录（未注册手机号自动建档）
     */
    LOGIN("LOGIN", "登录"),

    /**
     * 重置密码（手机号必须已注册）
     */
    RESET("RESET", "重置密码"),

    /**
     * 绑定手机号（手机号不能被其他账号占用）
     */
    BIND("BIND", "绑定手机号");

    private final String value;
    private final String description;
}
