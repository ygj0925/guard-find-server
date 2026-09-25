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
 * 状态变更操作人类型（对应 `gf_case_status_log.operator_type`）
 *
 * @author guardfind
 * @since 2026/9/24
 */
public enum CaseOperatorTypeEnum {

    /**
     * C 端用户（家属、志愿者）
     */
    APP("用户"),

    /**
     * 后台账号（审核、处置）
     */
    SYS("审核员"),

    /**
     * 系统（时效任务、自动降级）
     */
    SYSTEM("系统");

    private final String description;

    CaseOperatorTypeEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 依据编码解析，忽略大小写，未匹配时按 C 端处理
     *
     * @param value 编码
     * @return 操作人类型
     */
    public static CaseOperatorTypeEnum of(String value) {
        for (CaseOperatorTypeEnum item : values()) {
            if (item.name().equalsIgnoreCase(value)) {
                return item;
            }
        }
        return APP;
    }
}
