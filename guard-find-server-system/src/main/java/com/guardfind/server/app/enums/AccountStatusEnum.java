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

import com.guardfind.server.common.enums.GenderEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * App 用户账号状态枚举（对应 gf_app_user.account_status 的数值编码）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Getter
@RequiredArgsConstructor
public enum AccountStatusEnum {

    /**
     * 正常
     */
    NORMAL(1, "正常"),

    /**
     * 锁定（连续密码错误等风控触发）
     */
    LOCKED(2, "已锁定"),

    /**
     * 注销申请中（冷静期内）
     */
    CANCELLING(3, "注销申请中"),

    /**
     * 已注销
     */
    CANCELLED(4, "已注销");

    private final Integer value;
    private final String label;

    /**
     * 获取枚举实例
     *
     * @param value 值
     * @return 枚举实例，null 表示未匹配
     */
    public static AccountStatusEnum of(Integer value) {
        return value == null
            ? null
            : Arrays.stream(values()).filter(e -> e.value.equals(value)).findFirst().orElse(null);
    }

    /**
     * 性别编码复用公共枚举，集中在此声明以避免业务侧引用散落
     *
     * @param gender 性别
     * @return 编码值
     */
    public static Integer genderValue(GenderEnum gender) {
        return gender == null ? GenderEnum.UNKNOWN.getValue() : gender.getValue();
    }
}
