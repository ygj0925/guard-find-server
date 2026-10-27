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

package com.guardfind.server.ledger;

import com.guardfind.server.ledger.model.entity.LedgerRecordDO;
import com.guardfind.server.ledger.model.resp.LedgerSummaryResp;
import com.guardfind.server.ledger.support.LedgerSummaryCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link LedgerSummaryCalculator} 纯函数单测：区间口径、聚合、占比、趋势补零。
 *
 * @author guardfind
 * @since 2026/10/28
 */
class LedgerSummaryCalculatorTest {

    private static final LocalDate WED = LocalDate.of(2026, 10, 28); // 周三

    private static LedgerRecordDO record(int type, String amount, String category, LocalDate date) {
        LedgerRecordDO record = new LedgerRecordDO();
        record.setType(type);
        record.setAmount(new BigDecimal(amount));
        record.setCategory(category);
        record.setRecordDate(date);
        return record;
    }

    @Test
    @DisplayName("周区间：周一为起点、跨月锚点也按完整 7 天")
    void weekRangeStartsMonday() {
        LedgerSummaryCalculator.PeriodRange range = LedgerSummaryCalculator.rangeOf("week", WED);
        assertEquals(LocalDate.of(2026, 10, 26), range.start()); // 周一
        assertEquals(LocalDate.of(2026, 11, 1), range.end()); // 周日（跨月）
        assertEquals(true, range.daily());
    }

    @Test
    @DisplayName("月区间：自然月首尾；年区间：自然年首尾且趋势逐月")
    void monthAndYearRange() {
        LedgerSummaryCalculator.PeriodRange month = LedgerSummaryCalculator.rangeOf("month", WED);
        assertEquals(LocalDate.of(2026, 10, 1), month.start());
        assertEquals(LocalDate.of(2026, 10, 31), month.end());

        LedgerSummaryCalculator.PeriodRange year = LedgerSummaryCalculator.rangeOf("year", WED);
        assertEquals(LocalDate.of(2026, 1, 1), year.start());
        assertEquals(LocalDate.of(2026, 12, 31), year.end());
        assertEquals(false, year.daily());
    }

    @Test
    @DisplayName("非法 period 直接拒绝")
    void invalidPeriodRejected() {
        assertThrows(IllegalArgumentException.class, () -> LedgerSummaryCalculator.rangeOf("quarter", WED));
    }

    @Test
    @DisplayName("聚合：总支出/总收入/结余/笔数，分类按支出降序")
    void aggregateTotalsAndCategories() {
        List<LedgerRecordDO> records = List.of(record(1, "30.00", "food", LocalDate
            .of(2026, 10, 1)), record(1, "20.00", "transport", LocalDate
                .of(2026, 10, 2)), record(1, "50.00", "food", LocalDate
                    .of(2026, 10, 3)), record(2, "8000.00", "salary", LocalDate.of(2026, 10, 10)));
        LedgerSummaryResp resp = LedgerSummaryCalculator.calculate(records, LedgerSummaryCalculator
            .rangeOf("month", WED));

        assertEquals(0, new BigDecimal("100.00").compareTo(resp.getTotalExpense()));
        assertEquals(0, new BigDecimal("8000.00").compareTo(resp.getTotalIncome()));
        assertEquals(0, new BigDecimal("7900.00").compareTo(resp.getBalance()));
        assertEquals(4L, resp.getRecordCount());

        assertEquals("food", resp.getByCategory().get(0).getCategory());
        // 80/100 = 0.8000
        assertEquals(0, new BigDecimal("0.8000").compareTo(resp.getByCategory().get(0).getRatio()));
        assertEquals("transport", resp.getByCategory().get(1).getCategory());
    }

    @Test
    @DisplayName("趋势：空档补零，逐日标签 MM-dd")
    void trendZeroFilled() {
        List<LedgerRecordDO> records = List.of(record(1, "12.50", "food", LocalDate.of(2026, 10, 2)));
        LedgerSummaryResp resp = LedgerSummaryCalculator.calculate(records, LedgerSummaryCalculator
            .rangeOf("month", WED));

        assertEquals(31, resp.getTrend().size());
        assertEquals("10-01", resp.getTrend().get(0).getLabel());
        assertEquals(0, BigDecimal.ZERO.compareTo(resp.getTrend().get(0).getExpense()));
        assertEquals(0, new BigDecimal("12.50").compareTo(resp.getTrend().get(1).getExpense()));
    }

    @Test
    @DisplayName("年趋势：逐月 12 个点")
    void yearlyTrendHasTwelvePoints() {
        List<LedgerRecordDO> records = List.of(record(2, "3000.00", "salary", LocalDate.of(2026, 3, 15)));
        LedgerSummaryResp resp = LedgerSummaryCalculator.calculate(records, LedgerSummaryCalculator
            .rangeOf("year", WED));

        assertEquals(12, resp.getTrend().size());
        assertEquals("2026-03", resp.getTrend().get(2).getLabel());
        assertEquals(0, new BigDecimal("3000.00").compareTo(resp.getTrend().get(2).getIncome()));
    }

    @Test
    @DisplayName("空区间：全零但不缺趋势点")
    void emptyRangeAllZero() {
        LedgerSummaryResp resp = LedgerSummaryCalculator.calculate(List.of(), LedgerSummaryCalculator
            .rangeOf("week", WED));
        assertEquals(0L, resp.getRecordCount());
        assertEquals(0, BigDecimal.ZERO.compareTo(resp.getTotalExpense()));
        assertEquals(7, resp.getTrend().size());
    }
}
