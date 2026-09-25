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

package com.guardfind.server.findcase.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 寻人档案去重指纹工具
 *
 * <p>
 * 采集域与档案域共用同一规则，保证"站外采到的"和"家属自己发的"能被同一把钥匙收敛：
 * dedupeKey = SHA256(规范化姓名 + 性别 + 失踪日期(精确到日，空则空串) + 失踪地区划码到市)
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public final class CaseDedupeKeyUtil {

    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private CaseDedupeKeyUtil() {
    }

    /**
     * 计算去重指纹
     *
     * @param name         姓名
     * @param gender       性别编码（0/1/2，null 视为 0）
     * @param missingTime  失踪时间
     * @param provinceCode 省编码
     * @param cityCode     市编码（为空时退化到省编码）
     * @return 去重指纹
     */
    public static String build(String name,
                               Integer gender,
                               LocalDateTime missingTime,
                               String provinceCode,
                               String cityCode) {
        String raw = "%s|%s|%s|%s".formatted(normalizeName(name), gender == null ? 0 : gender, missingTime == null
            ? ""
            : missingTime.format(DAY_FORMATTER), StrUtil.blankToDefault(cityCode, StrUtil
                .blankToDefault(provinceCode, "")));
        return DigestUtil.sha256Hex(raw);
    }

    /**
     * 规范化姓名：统一小写、全角转半角、去掉所有空白（含全角空格 \u3000）。
     *
     * <p>
     * 采集来源的姓名常见"张 三 合""张三合"两种排版，必须收敛到同一指纹；
     * 这里不依赖 Hutool 的 toDBC（当前版本无该方法），自行折叠 FF01-FF5E 与全角空格。
     * </p>
     *
     * @param name 原始姓名
     * @return 规范化姓名
     */
    public static String normalizeName(String name) {
        if (StrUtil.isBlank(name)) {
            return "";
        }
        String lower = name.toLowerCase();
        StringBuilder builder = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == '　') {
                // 全角空格视为空白
                continue;
            }
            if (c >= '＀' && c <= '～') {
                c = (char)(c - 0xFEE0);
            }
            if (Character.isWhitespace(c)) {
                continue;
            }
            builder.append(c);
        }
        return builder.toString();
    }
}
