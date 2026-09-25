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

package com.guardfind.server.moderation.enums;

/**
 * 用户处置类型（对应 {@code gf_user_punishment.type}，字典 {@code gf_punishment_type}）
 *
 * @author guardfind
 * @since 2026/9/24
 */
public enum PunishmentTypeEnum {

    /**
     * 禁言
     */
    MUTE("禁言", 7),

    /**
     * 封禁
     */
    BAN("封禁", 30);

    private final String description;

    /**
     * 未指定时长时的默认天数（永久处置传 null）
     */
    private final int defaultDays;

    PunishmentTypeEnum(String description, int defaultDays) {
        this.description = description;
        this.defaultDays = defaultDays;
    }

    public String getDescription() {
        return description;
    }

    public int getDefaultDays() {
        return defaultDays;
    }

    /**
     * 依据编码解析，忽略大小写
     *
     * @param value 编码
     * @return 枚举，未匹配时为 null
     */
    public static PunishmentTypeEnum of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (PunishmentTypeEnum item : values()) {
            if (item.name().equalsIgnoreCase(value.trim())) {
                return item;
            }
        }
        return null;
    }
}
