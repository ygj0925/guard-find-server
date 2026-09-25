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

import java.util.Arrays;

/**
 * 年龄段（与字典 `gf_age_group` 的取值一一对应，用于广场筛选与卡片展示）
 *
 * <p>
 * 档案若未显式登记年龄段，则由"失踪时年龄"推导，保证筛选口径稳定；年龄缺失时不落到任何段。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public enum AgeGroupEnum {

    /**
     * 0-6 岁
     */
    AGE_0_6("0_6", "0-6 岁", 0, 6),

    /**
     * 7-12 岁
     */
    AGE_7_12("7_12", "7-12 岁", 7, 12),

    /**
     * 13-17 岁
     */
    AGE_13_17("13_17", "13-17 岁", 13, 17),

    /**
     * 18-59 岁
     */
    AGE_18_59("18_59", "18-59 岁", 18, 59),

    /**
     * 60 岁及以上
     */
    AGE_60_PLUS("60_PLUS", "60 岁及以上", 60, Integer.MAX_VALUE);

    private final String value;
    private final String label;
    private final int minAge;
    private final int maxAge;

    AgeGroupEnum(String value, String label, int minAge, int maxAge) {
        this.value = value;
        this.label = label;
        this.minAge = minAge;
        this.maxAge = maxAge;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public int getMinAge() {
        return minAge;
    }

    public int getMaxAge() {
        return maxAge;
    }

    /**
     * 依据失踪时年龄推导年龄段
     *
     * @param ageAtMissing 失踪时年龄（可为空）
     * @return 年龄段，无法判定时为 null
     */
    public static AgeGroupEnum of(Integer ageAtMissing) {
        if (ageAtMissing == null || ageAtMissing < 0) {
            return null;
        }
        return Arrays.stream(values())
            .filter(ageGroup -> ageAtMissing >= ageGroup.minAge && ageAtMissing <= ageGroup.maxAge)
            .findFirst()
            .orElse(null);
    }

    /**
     * 依据字典取值解析年龄段
     *
     * @param value 字典取值（如 0_6）
     * @return 年龄段，未匹配时为 null
     */
    public static AgeGroupEnum of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
            .filter(ageGroup -> ageGroup.value.equalsIgnoreCase(value) || ageGroup.name().equalsIgnoreCase(value))
            .findFirst()
            .orElse(null);
    }
}
