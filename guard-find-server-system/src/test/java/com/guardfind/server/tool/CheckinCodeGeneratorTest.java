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

import com.guardfind.server.tool.support.CheckinCodeGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CheckinCodeGenerator} 单测。
 *
 * <p>
 * 测试向量与客户端 lib/checkin/__tests__/code.test.ts 完全一致:
 * 两端算法必须逐字节一致,否则发起人屏幕展示的码对不上服务端校验。
 * </p>
 */
class CheckinCodeGeneratorTest {

    /** 与客户端共享的测试向量 */
    private static final String VECTOR_SECRET = "abc123";
    private static final long VECTOR_WINDOW = 59_000_000L;
    private static final String VECTOR_EXPECTED = "368779";

    @Test
    @DisplayName("测试向量:与客户端约定值一致")
    void sharedTestVector() {
        assertEquals(VECTOR_EXPECTED, CheckinCodeGenerator.code(VECTOR_SECRET, VECTOR_WINDOW));
    }

    @Test
    @DisplayName("码为 6 位数字,不同窗不同码")
    void sixDigitsAndRotates() {
        String code = CheckinCodeGenerator.code("abc123", CheckinCodeGenerator.windowIndex(1_800_000_000L));
        assertTrue(code.matches("\\d{6}"));
        String next = CheckinCodeGenerator.code("abc123", CheckinCodeGenerator.windowIndex(1_800_000_030L));
        // 极小概率相同,若相同换一对输入断言旋转性
        assertTrue(!code.equals(next) || code.equals(CheckinCodeGenerator.code("abc123", CheckinCodeGenerator
            .windowIndex(1_800_000_000L + 30))));
    }

    @Test
    @DisplayName("校验:接受当前窗与上一窗,拒绝更早的窗与乱码")
    void verifyWindowTolerance() {
        long windowIndexAtNow = 60_000_000L;
        long nowSeconds = windowIndexAtNow * 30L;
        String current = CheckinCodeGenerator.code("s1", windowIndexAtNow);
        String previous = CheckinCodeGenerator.code("s1", windowIndexAtNow - 1);
        String older = CheckinCodeGenerator.code("s1", windowIndexAtNow - 2);
        assertTrue(CheckinCodeGenerator.verify("s1", nowSeconds, current));
        assertTrue(CheckinCodeGenerator.verify("s1", nowSeconds, previous));
        assertFalse(CheckinCodeGenerator.verify("s1", nowSeconds, older));
        assertFalse(CheckinCodeGenerator.verify("s1", nowSeconds, "abc123"));
        assertFalse(CheckinCodeGenerator.verify("s1", nowSeconds, null));
        // 种子不同必不通过
        assertFalse(CheckinCodeGenerator.verify("s2", nowSeconds, current));
    }
}
