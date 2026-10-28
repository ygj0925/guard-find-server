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

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * KR 进度计算纯函数(无 Spring 依赖)
 *
 * <p>
 * 口径:percent = current / target,封顶 [0,1];目标值 <= 0 视为无效记 0;
 * current >= target 即视为完成(由调用方同时落 status)。
 * </p>
 */
public final class KrProgressCalculator {

    private KrProgressCalculator() {
    }

    /**
     * @return 进度比例(0-1,4 位小数)
     */
    public static BigDecimal percent(BigDecimal current, BigDecimal target) {
        if (current == null || target == null || target.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (current.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return current.min(target).divide(target, 4, RoundingMode.HALF_UP);
    }

    /**
     * 当前值是否已达成(>= 目标)
     */
    public static boolean achieved(BigDecimal current, BigDecimal target) {
        return target != null && target.signum() > 0 && current != null && current.compareTo(target) >= 0;
    }
}
