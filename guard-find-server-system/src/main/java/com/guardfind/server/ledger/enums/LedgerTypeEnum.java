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

package com.guardfind.server.ledger.enums;

import lombok.Getter;
import top.continew.starter.core.exception.BusinessException;

import java.util.Arrays;

/**
 * 记账收支类型（契约与客户端 lib/api/endpoints/ledger.ts 对齐）
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Getter
public enum LedgerTypeEnum {

    EXPENSE(1, "支出"), INCOME(2, "收入");

    private final int code;
    private final String description;

    LedgerTypeEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static LedgerTypeEnum of(int code) {
        return Arrays.stream(values())
            .filter(item -> item.code == code)
            .findFirst()
            .orElseThrow(() -> new BusinessException("收支类型不合法"));
    }
}
