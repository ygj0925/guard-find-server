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

import java.util.Arrays;

/**
 * App 用户身份类型（对应 gf_app_user.identity_type 存储的字符串）
 *
 * @author guardfind
 * @since 2026/9/24
 */
public enum IdentityTypeEnum {

    /**
     * 家属（失踪/走失人员亲属）
     */
    GUARDIAN,

    /**
     * 志愿者
     */
    VOLUNTEER,

    /**
     * 普通用户（好心人）
     */
    PUBLIC;

    public String label() {
        return switch (this) {
            case GUARDIAN -> "家属";
            case VOLUNTEER -> "志愿者";
            case PUBLIC -> "普通用户";
        };
    }

    public static IdentityTypeEnum of(String value) {
        return Arrays.stream(values()).filter(e -> e.name().equalsIgnoreCase(value)).findFirst().orElse(PUBLIC);
    }
}
