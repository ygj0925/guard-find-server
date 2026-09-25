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
 * 敏感词类别
 *
 * <p>
 * 处置强度由 {@link #isReject()} 决定：{@code ILLEGAL}/{@code ABUSE} 命中即拒绝提交，
 * {@code FRAUD} 命中放行但标记高风险并进入人工审核队列（避免误杀真实求助文本）。
 * {@link #getSeverity()} 越大越严重，用于一次匹配出多个词时取"最差类别"。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public enum SensitiveCategoryEnum {

    /**
     * 诈骗话术（放行 + 高风险标记）
     */
    FRAUD("诈骗话术", 1, false),

    /**
     * 辱骂骚扰（拒绝）
     */
    ABUSE("辱骂骚扰", 2, true),

    /**
     * 违法违规（拒绝）
     */
    ILLEGAL("违法违规", 3, true);

    private final String description;
    private final int severity;
    private final boolean reject;

    SensitiveCategoryEnum(String description, int severity, boolean reject) {
        this.description = description;
        this.severity = severity;
        this.reject = reject;
    }

    public String getDescription() {
        return description;
    }

    public int getSeverity() {
        return severity;
    }

    /**
     * 命中该类别是否直接拒绝提交
     *
     * @return true：拒绝
     */
    public boolean isReject() {
        return reject;
    }

    /**
     * 依据编码解析，忽略大小写
     *
     * @param value 编码
     * @return 类别，未匹配时为 null
     */
    public static SensitiveCategoryEnum of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (SensitiveCategoryEnum category : values()) {
            if (category.name().equalsIgnoreCase(value.trim())) {
                return category;
            }
        }
        return null;
    }

    /**
     * 取两者中更严重的类别
     *
     * @param left  左值（可为 null）
     * @param right 右值（可为 null）
     * @return 更严重的类别，均为 null 时返回 null
     */
    public static SensitiveCategoryEnum worst(SensitiveCategoryEnum left, SensitiveCategoryEnum right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.severity >= right.severity ? left : right;
    }
}
