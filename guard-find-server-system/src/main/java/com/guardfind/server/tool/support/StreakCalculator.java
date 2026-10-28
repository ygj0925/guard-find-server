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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * 打卡 streak 纯函数(无 Spring 依赖,便于单测)
 *
 * <p>
 * 口径:当前连续 = 以「今天」为终点(今天未打卡则宽容回退到昨天,避免当天还没打卡
 * 就把 streak 显示成 0)的最长连续日期串;最长连续 = 历史全部日期里的最大连续串。
 * </p>
 *
 * @author guardfind
 * @since 2026/10/28
 */
public final class StreakCalculator {

    private StreakCalculator() {
    }

    /**
     * @param checkinDates 打卡日期(任意顺序,允许重复)
     * @param today        今天
     * @return [0]=当前连续, [1]=最长连续, [2]=累计天数
     */
    public static long[] calculate(Collection<LocalDate> checkinDates, LocalDate today) {
        Set<LocalDate> days = new HashSet<>(checkinDates);
        if (days.isEmpty()) {
            return new long[] {0, 0, 0};
        }
        long totalDays = days.size();
        long bestStreak = 0;
        long run = 0;
        LocalDate previous = null;
        for (LocalDate day : days.stream().sorted().toList()) {
            run = previous != null && ChronoUnit.DAYS.between(previous, day) == 1 ? run + 1 : 1;
            bestStreak = Math.max(bestStreak, run);
            previous = day;
        }

        long currentStreak = 0;
        LocalDate cursor = days.contains(today) ? today : today.minusDays(1);
        while (days.contains(cursor)) {
            currentStreak++;
            cursor = cursor.minusDays(1);
        }
        return new long[] {currentStreak, bestStreak, totalDays};
    }

    /** 排序辅助(月历渲染用):升序返回日期。 */
    public static java.util.List<LocalDate> sorted(Collection<LocalDate> dates) {
        return dates.stream().distinct().sorted(Comparator.naturalOrder()).toList();
    }
}
