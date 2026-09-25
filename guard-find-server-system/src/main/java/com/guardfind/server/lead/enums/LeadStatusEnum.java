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

package com.guardfind.server.lead.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * 寻人线索状态
 *
 * <p>
 * {@code PENDING} 为提交后的初始态，处置接口只能在 {@code ACCEPTED / REJECTED / CONTACTED} 三者间流转，
 * 避免"把线索改回待处理"造成家属端与通知语义混乱。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public enum LeadStatusEnum {

    /**
     * 待处理（新提交）
     */
    PENDING("待处理"),

    /**
     * 已采纳
     */
    ACCEPTED("已采纳"),

    /**
     * 已排除
     */
    REJECTED("已排除"),

    /**
     * 已联系提交人
     */
    CONTACTED("已联系");

    private final String description;

    LeadStatusEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 可处置目标状态集合
     *
     * @return 状态集合
     */
    public static Set<LeadStatusEnum> actionable() {
        return EnumSet.of(ACCEPTED, REJECTED, CONTACTED);
    }

    /**
     * 依据编码解析，忽略大小写
     *
     * @param value 编码
     * @return 状态，未匹配时为 null
     */
    public static LeadStatusEnum of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (LeadStatusEnum status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return null;
    }
}
