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

package com.guardfind.server.findcase.enums;

/**
 * 寻人档案紧急程度
 *
 * @author guardfind
 * @since 2026/9/24
 */
public enum UrgencyLevelEnum {

    /**
     * 常规
     */
    NORMAL("常规"),

    /**
     * 紧急（失踪 72 小时内，黄金 72 小时）
     */
    URGENT("紧急"),

    /**
     * 长期未破
     */
    STANDING("长期未破");

    private final String description;

    UrgencyLevelEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 黄金 72 小时阈值（小时）
     */
    public static final int GOLDEN_HOURS = 72;

    /**
     * 依据失踪时间推断紧急程度
     *
     * @param missingTime 失踪时间（可为 null）
     * @param now         当前时间
     * @return 紧急程度
     */
    public static UrgencyLevelEnum evaluate(java.time.LocalDateTime missingTime, java.time.LocalDateTime now) {
        if (missingTime == null) {
            return NORMAL;
        }
        long hours = java.time.Duration.between(missingTime, now).toHours();
        if (hours < 0) {
            return URGENT;
        }
        if (hours <= GOLDEN_HOURS) {
            return URGENT;
        }
        return java.time.Duration.between(missingTime, now).toDays() > 3650 ? STANDING : NORMAL;
    }
}
