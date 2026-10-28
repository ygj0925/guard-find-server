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

import java.nio.charset.StandardCharsets;

/**
 * 签到动态码(纯函数,防代签核心)。
 *
 * <p>
 * 算法:FNV-1a 32 位哈希,输入 = secret + ':' + 时间窗序号(默认 30 秒一格),
 * 输出 6 位数字码。客户端(lib/checkin/code.ts)用同一算法与同一组测试向量互验,
 * 保证发起人屏幕上展示的码与服务端校验一致。服务端同时接受上一窗的码(时钟偏移宽容)。
 * </p>
 */
public final class CheckinCodeGenerator {

    /** 动态码时间窗(秒) */
    public static final int WINDOW_SECONDS = 30;

    private CheckinCodeGenerator() {
    }

    /**
     * 生成某时间窗的 6 位数字码
     *
     * @param secret      活动种子
     * @param windowIndex 时间窗序号(epochSeconds / windowSeconds)
     */
    public static String code(String secret, long windowIndex) {
        String input = secret + ":" + windowIndex;
        // FNV-1a 32 位;Java int 溢出截断与 JS Math.imul 一致
        int hash = 0x811c9dc5;
        for (byte b : input.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xff;
            hash *= 16777619;
        }
        // 掩掉符号位,保证两端在"无符号 31 位"上取模一致
        int positive = hash & 0x7fffffff;
        return String.format("%06d", positive % 1_000_000);
    }

    /**
     * 当前时间窗序号
     */
    public static long windowIndex(long epochSeconds) {
        return epochSeconds / WINDOW_SECONDS;
    }

    /**
     * 校验:接受当前窗与上一窗(时钟偏移宽容)
     */
    public static boolean verify(String secret, long epochSeconds, String inputCode) {
        if (inputCode == null || !inputCode.matches("\\d{6}")) {
            return false;
        }
        long index = windowIndex(epochSeconds);
        return code(secret, index).equals(inputCode) || code(secret, index - 1).equals(inputCode);
    }
}
