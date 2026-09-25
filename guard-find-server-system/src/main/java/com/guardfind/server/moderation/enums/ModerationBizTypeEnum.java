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
 * 审核队列业务类型（对应 {@code /system/moderation/{bizType}/{id}/...}）
 *
 * <p>
 * {@code COLLECT_CANDIDATE} 的审批归属采集域（{@code /system/collect/candidate/**}，权限码
 * {@code system:collect:candidate:*}），内容治理队列不代为审批，避免两套审批入口写出不同结论。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public enum ModerationBizTypeEnum {

    /**
     * 寻人档案
     */
    CASE("寻人档案"),

    /**
     * 高风险线索
     */
    LEAD("高风险线索"),

    /**
     * 采集候选（由采集域自行审批）
     */
    COLLECT_CANDIDATE("采集候选");

    private final String description;

    ModerationBizTypeEnum(String description) {
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
    public static ModerationBizTypeEnum of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (ModerationBizTypeEnum item : values()) {
            if (item.name().equalsIgnoreCase(value.trim())) {
                return item;
            }
        }
        return null;
    }
}
