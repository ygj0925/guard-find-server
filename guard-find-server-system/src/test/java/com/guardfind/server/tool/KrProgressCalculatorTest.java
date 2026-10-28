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

import com.guardfind.server.tool.support.KrProgressCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link KrProgressCalculator} 纯函数单测:进度口径、封顶、达成判定。
 */
class KrProgressCalculatorTest {

    @Test
    @DisplayName("常规进度:42.5/100 = 0.4250")
    void normalPercent() {
        assertEquals(0, new BigDecimal("0.4250").compareTo(KrProgressCalculator
            .percent(new BigDecimal("42.5"), new BigDecimal("100"))));
    }

    @Test
    @DisplayName("超额封顶 1,不出现大于 1 的进度")
    void cappedAtOne() {
        assertEquals(0, BigDecimal.ONE.compareTo(KrProgressCalculator
            .percent(new BigDecimal("150"), new BigDecimal("100"))));
    }

    @Test
    @DisplayName("目标值非正或当前值为 0/负,进度记 0")
    void invalidInputsZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(KrProgressCalculator
            .percent(BigDecimal.ZERO, new BigDecimal("100"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(KrProgressCalculator.percent(new BigDecimal("10"), BigDecimal.ZERO)));
        assertEquals(0, BigDecimal.ZERO.compareTo(KrProgressCalculator.percent(new BigDecimal("10"), null)));
    }

    @Test
    @DisplayName("达成判定:>= 目标即达成")
    void achievedCheck() {
        assertTrue(KrProgressCalculator.achieved(new BigDecimal("100"), new BigDecimal("100")));
        assertTrue(KrProgressCalculator.achieved(new BigDecimal("120"), new BigDecimal("100")));
        assertFalse(KrProgressCalculator.achieved(new BigDecimal("99.99"), new BigDecimal("100")));
        assertFalse(KrProgressCalculator.achieved(null, new BigDecimal("100")));
    }
}
