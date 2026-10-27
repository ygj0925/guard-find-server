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

import cn.hutool.core.util.StrUtil;
import top.continew.starter.core.exception.BusinessException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 汇总周期参数解析（纯函数）
 *
 * <p>
 * period 合法取值 week/month/year；date 为区间内锚点日，缺省取今天。
 * 非法入参统一抛 {@link BusinessException}，与 C 端错误话术一致。
 * </p>
 *
 * @author guardfind
 * @since 2026/10/28
 */
public final class PeriodParser {

    private PeriodParser() {
    }

    /**
     * 解析周期区间
     *
     * @param period week | month | year
     * @param date   yyyy-MM-dd，可空
     */
    public static LedgerSummaryCalculator.PeriodRange parse(String period, String date) {
        if (StrUtil.isBlank(period)) {
            throw new BusinessException("汇总周期不能为空");
        }
        LocalDate anchor = LocalDate.now();
        if (StrUtil.isNotBlank(date)) {
            try {
                anchor = LocalDate.parse(date);
            } catch (DateTimeParseException e) {
                throw new BusinessException("日期格式应为 yyyy-MM-dd");
            }
        }
        try {
            return LedgerSummaryCalculator.rangeOf(period, anchor);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        }
    }
}
