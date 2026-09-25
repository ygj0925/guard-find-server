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

package com.guardfind.server.collect.enums;

/**
 * 采集候选状态
 *
 * <p>
 * 采集数据必须经人工审核才对外可见：{@code PENDING} 为待审，{@code DUPLICATED} 表示被同指纹的更完整候选收敛（见
 * {@code primaryId}），{@code ERASED} 为响应站方或删除权请求后的终态（对外不可见）。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public enum CandidateStatusEnum {

    /**
     * 待审核
     */
    PENDING("待审核"),

    /**
     * 已通过（已生成寻人档案）
     */
    APPROVED("已通过"),

    /**
     * 已驳回
     */
    REJECTED("已驳回"),

    /**
     * 已合并到既有档案
     */
    MERGED("已合并"),

    /**
     * 已去重（指向主候选）
     */
    DUPLICATED("已去重"),

    /**
     * 已清除（终态，对外不可见）
     */
    ERASED("已清除");

    private final String description;

    CandidateStatusEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 解析状态，忽略大小写
     *
     * @param value 状态值
     * @return 状态，未匹配时为 null
     */
    public static CandidateStatusEnum of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (CandidateStatusEnum status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return null;
    }
}
