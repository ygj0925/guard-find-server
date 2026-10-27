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

package com.guardfind.server.ledger.support;

import com.guardfind.server.ledger.enums.LedgerTypeEnum;

import java.util.Map;
import java.util.Set;

/**
 * 记账分类契约（与客户端 lib/ledger/categories.ts 一一对应）
 *
 * <p>
 * 一期只内置 13 个分类不做自定义：降低理解成本，也避免分类字典表的引入。
 * 分类码必须与所属收支类型匹配，服务端在新增/修改时校验。
 * </p>
 *
 * @author guardfind
 * @since 2026/10/28
 */
public final class LedgerCategories {

    /** 支出分类码 */
    public static final Set<String> EXPENSE = Set
        .of("food", "transport", "shopping", "housing", "entertainment", "medical", "education", "social", "other");

    /** 收入分类码 */
    public static final Set<String> INCOME = Set.of("salary", "investment", "redpacket", "other");

    private static final Map<LedgerTypeEnum, Set<String>> CATEGORIES = Map
        .of(LedgerTypeEnum.EXPENSE, EXPENSE, LedgerTypeEnum.INCOME, INCOME);

    private LedgerCategories() {
    }

    /**
     * 分类码是否属于该收支类型
     */
    public static boolean isValid(LedgerTypeEnum type, String category) {
        return CATEGORIES.get(type).contains(category);
    }
}
