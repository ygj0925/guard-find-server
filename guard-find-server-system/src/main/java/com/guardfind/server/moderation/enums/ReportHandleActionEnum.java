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
 * 举报处置动作（契约 §6 {@code /system/moderation/report/{id}/handle} 的 action）
 *
 * @author guardfind
 * @since 2026/9/24
 */
public enum ReportHandleActionEnum {

    /**
     * 举报成立
     */
    APPROVE_REPORT("举报成立"),

    /**
     * 举报不成立
     */
    DISMISS("举报不成立");

    private final String description;

    ReportHandleActionEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 依据编码解析，忽略大小写
     *
     * @param value 编码
     * @return 枚举，未匹配时为 null
     */
    public static ReportHandleActionEnum of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (ReportHandleActionEnum item : values()) {
            if (item.name().equalsIgnoreCase(value.trim())) {
                return item;
            }
        }
        return null;
    }
}
