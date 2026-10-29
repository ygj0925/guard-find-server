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

package com.guardfind.server.tool.support;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * KR 状态自动判定(纯函数,飞书口径):
 * 时间进度(周期内已过天数占比) vs 实际进度占比:
 * 实际 ≥ 时间进度 → ON_TRACK(正常)
 * 实际 < 时间进度 × 0.8 → AT_RISK(有风险)
 * 实际 < 时间进度 × 0.5 → BEHIND(落后)
 * 已完成恒为 DONE;周期未开始/已结束特殊处理(结束且未完成 = BEHIND)。
 */
public final class KrStatusCalculator {

    public static final String ON_TRACK = "onTrack";
    public static final String AT_RISK = "atRisk";
    public static final String BEHIND = "behind";
    public static final String DONE = "done";

    private KrStatusCalculator() {
    }

    /**
     * @param periodStart 周期开始(yyyy-MM-01 或季度首日,由调用方从 period 推导)
     * @param periodEnd   周期结束
     * @param progress    实际进度 0-1
     * @param done        KR 是否已完成
     */
    public static String status(LocalDate periodStart, LocalDate periodEnd, double progress, boolean done) {
        if (done) {
            return DONE;
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(periodStart)) {
            return ON_TRACK;
        }
        if (today.isAfter(periodEnd)) {
            return BEHIND;
        }
        long total = Math.max(1, ChronoUnit.DAYS.between(periodStart, periodEnd));
        double timeRatio = (double)ChronoUnit.DAYS.between(periodStart, today) / total;
        if (progress >= timeRatio) {
            return ON_TRACK;
        }
        if (progress >= timeRatio * 0.5) {
            return AT_RISK;
        }
        return BEHIND;
    }

    /**
     * 周期文本(yyyy 或 yyyyQn)→ 起止日期
     */
    public static LocalDate[] periodRange(String period) {
        if (period.matches("\\d{4}Q[1-4]")) {
            int year = Integer.parseInt(period.substring(0, 4));
            int q = Integer.parseInt(period.substring(5));
            return new LocalDate[] {LocalDate.of(year, (q - 1) * 3 + 1, 1), LocalDate.of(year, q * 3, 1)
                .withDayOfMonth(LocalDate.of(year, q * 3, 1).lengthOfMonth()),};
        }
        if (period.matches("\\d{4}")) {
            int year = Integer.parseInt(period);
            return new LocalDate[] {LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)};
        }
        LocalDate today = LocalDate.now();
        return new LocalDate[] {today.withDayOfYear(1), today.withDayOfYear(today.lengthOfYear())};
    }
}
