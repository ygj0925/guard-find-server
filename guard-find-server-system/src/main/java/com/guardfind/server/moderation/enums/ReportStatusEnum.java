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
 * 举报处理状态（对应 {@code gf_report.status}）
 *
 * @author guardfind
 * @since 2026/9/24
 */
public enum ReportStatusEnum {

    /**
     * 待处理
     */
    PENDING("待处理"),

    /**
     * 举报成立
     */
    APPROVED("举报成立"),

    /**
     * 举报不成立（已驳回）
     */
    DISMISSED("举报不成立");

    private final String description;

    ReportStatusEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
