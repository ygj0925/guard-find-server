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
import com.guardfind.server.ledger.model.entity.LedgerRecordDO;
import com.guardfind.server.ledger.model.resp.LedgerSummaryResp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 记账汇总计算（纯函数，无 Spring 依赖，便于单测）
 *
 * <p>
 * 一期数据量下直接在内存聚合区间内流水，不写 SQL 聚合；
 * 汇总口径：周 = 周一起始的完整 7 天，月 = 自然月，年 = 自然年。
 * 趋势点空档补零，客户端按序渲染即可。
 * </p>
 *
 * @author guardfind
 * @since 2026/10/28
 */
public final class LedgerSummaryCalculator {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter ISO_DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private LedgerSummaryCalculator() {
    }

    /**
     * 汇总区间定义
     */
    public record PeriodRange(LocalDate start, LocalDate end, boolean daily) {
    }

    /**
     * 解析周期区间：period = week|month|year，date 是区间内的锚点日
     */
    public static PeriodRange rangeOf(String period, LocalDate date) {
        return switch (period) {
            case "week" -> new PeriodRange(date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)), date
                .with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY)), true);
            case "month" -> {
                YearMonth month = YearMonth.from(date);
                yield new PeriodRange(month.atDay(1), month.atEndOfMonth(), true);
            }
            case "year" -> new PeriodRange(date.with(TemporalAdjusters.firstDayOfYear()), date.with(TemporalAdjusters
                .lastDayOfYear()), false);
            default -> throw new IllegalArgumentException("period 仅支持 week/month/year");
        };
    }

    /**
     * 聚合区间内流水：总计、分类占比、趋势
     */
    public static LedgerSummaryResp calculate(List<LedgerRecordDO> records, PeriodRange range) {
        LedgerSummaryResp resp = new LedgerSummaryResp();
        BigDecimal totalExpense = ZERO;
        BigDecimal totalIncome = ZERO;

        // 分类统计：LinkedHashMap 保持插入序，最后按支出金额排序输出
        Map<String, BigDecimal[]> byCategory = new LinkedHashMap<>();
        for (LedgerRecordDO record : records) {
            boolean expense = record.getType() == LedgerTypeEnum.EXPENSE.getCode();
            if (expense) {
                totalExpense = totalExpense.add(record.getAmount());
            } else {
                totalIncome = totalIncome.add(record.getAmount());
            }
            BigDecimal[] stat = byCategory.computeIfAbsent(record.getCategory(), k -> new BigDecimal[] {ZERO, ZERO});
            stat[0] = expense ? stat[0].add(record.getAmount()) : stat[0];
            stat[1] = expense ? stat[1] : stat[1].add(record.getAmount());
        }

        List<LedgerSummaryResp.CategoryStat> categoryStats = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> entry : byCategory.entrySet()) {
            LedgerSummaryResp.CategoryStat stat = new LedgerSummaryResp.CategoryStat();
            stat.setCategory(entry.getKey());
            stat.setExpense(entry.getValue()[0]);
            stat.setIncome(entry.getValue()[1]);
            stat.setRatio(totalExpense.signum() > 0
                ? entry.getValue()[0].divide(totalExpense, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
            categoryStats.add(stat);
        }
        categoryStats.sort((a, b) -> b.getExpense().compareTo(a.getExpense()));

        resp.setTotalExpense(totalExpense);
        resp.setTotalIncome(totalIncome);
        resp.setBalance(totalIncome.subtract(totalExpense));
        resp.setRecordCount((long)records.size());
        resp.setByCategory(categoryStats);
        resp.setTrend(buildTrend(records, range));
        resp.setStartDate(range.start().format(ISO_DAY));
        resp.setEndDate(range.end().format(ISO_DAY));
        return resp;
    }

    /**
     * 趋势点：逐日（周/月）或逐月（年），区间内空档补零
     */
    private static List<LedgerSummaryResp.TrendPoint> buildTrend(List<LedgerRecordDO> records, PeriodRange range) {
        Map<String, BigDecimal[]> buckets = new LinkedHashMap<>();
        if (range.daily()) {
            for (LocalDate day = range.start(); !day.isAfter(range.end()); day = day.plusDays(1)) {
                buckets.put(day.format(DAY_LABEL), new BigDecimal[] {ZERO, ZERO});
            }
        } else {
            YearMonth cursor = YearMonth.from(range.start());
            YearMonth endMonth = YearMonth.from(range.end());
            while (!cursor.isAfter(endMonth)) {
                buckets.put(cursor.format(MONTH_LABEL), new BigDecimal[] {ZERO, ZERO});
                cursor = cursor.plusMonths(1);
            }
        }

        for (LedgerRecordDO record : records) {
            String label = range.daily()
                ? record.getRecordDate().format(DAY_LABEL)
                : YearMonth.from(record.getRecordDate()).format(MONTH_LABEL);
            BigDecimal[] bucket = buckets.get(label);
            if (bucket == null) {
                // 流水日期理论上有索引约束不会越界；越界说明数据异常，忽略而不崩报表
                continue;
            }
            if (record.getType() == LedgerTypeEnum.EXPENSE.getCode()) {
                bucket[0] = bucket[0].add(record.getAmount());
            } else {
                bucket[1] = bucket[1].add(record.getAmount());
            }
        }

        List<LedgerSummaryResp.TrendPoint> trend = new ArrayList<>(buckets.size());
        buckets.forEach((label, value) -> {
            LedgerSummaryResp.TrendPoint point = new LedgerSummaryResp.TrendPoint();
            point.setLabel(label);
            point.setExpense(value[0]);
            point.setIncome(value[1]);
            trend.add(point);
        });
        return trend;
    }

    /**
     * 比例转百分数文案用（0.3529 -> 35.29），仅供测试与调试
     */
    public static BigDecimal toPercent(BigDecimal ratio) {
        return ratio.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    }
}
