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

package com.guardfind.server.tool;

import com.guardfind.server.tool.support.StreakCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link StreakCalculator} 纯函数单测:宽容回退、断档清零、最长连续。
 *
 * @author guardfind
 * @since 2026/10/28
 */
class StreakCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    @Test
    @DisplayName("今天已打卡:streak 从今天往前数")
    void streakIncludesToday() {
        long[] stats = StreakCalculator.calculate(Set.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2)), TODAY);
        assertEquals(3, stats[0]);
        assertEquals(3, stats[1]);
        assertEquals(3, stats[2]);
    }

    @Test
    @DisplayName("今天未打卡:宽容按昨天截止,不清零")
    void streakGraceWhenTodayMissing() {
        long[] stats = StreakCalculator.calculate(Set.of(TODAY.minusDays(1), TODAY.minusDays(2)), TODAY);
        assertEquals(2, stats[0]);
    }

    @Test
    @DisplayName("昨天也断档:当前清零,但最长连续保留")
    void streakZeroButBestKept() {
        long[] stats = StreakCalculator.calculate(Set.of(TODAY.minusDays(5), TODAY.minusDays(6), TODAY
            .minusDays(7)), TODAY);
        assertEquals(0, stats[0]);
        assertEquals(3, stats[1]);
        assertEquals(3, stats[2]);
    }

    @Test
    @DisplayName("乱序+重复输入也能正确统计")
    void unorderedDuplicates() {
        long[] stats = StreakCalculator.calculate(List.of(TODAY, TODAY, TODAY.minusDays(1), TODAY.minusDays(3)), TODAY);
        assertEquals(2, stats[0]);
        assertEquals(2, stats[1]);
        assertEquals(3, stats[2]);
    }

    @Test
    @DisplayName("空输入全零")
    void emptyAllZero() {
        long[] stats = StreakCalculator.calculate(List.of(), TODAY);
        assertEquals(0, stats[0]);
        assertEquals(0, stats[1]);
        assertEquals(0, stats[2]);
    }
}
