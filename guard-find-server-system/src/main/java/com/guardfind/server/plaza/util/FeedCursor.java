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

package com.guardfind.server.plaza.util;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import top.continew.starter.core.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 信息流游标
 *
 * <p>
 * 游标为 {@code Base64URL(版本|排序键毫秒|主键 ID)}，排序键取行的时间列（发布/找回时间）毫秒值，主键作次级键。
 * 翻页时把"上一页最后一行"的这两个值作为边界下推给 SQL，因此即使翻页期间有新档案发布，也不会重复或漏掉已经翻过的记录
 * （区别于 offset 深翻：offset 会因新行插入而整体位移）。
 * </p>
 *
 * @param sortKey 排序键（时间列毫秒值）
 * @param id      主键 ID
 * @author guardfind
 * @since 2026/9/24
 */
public record FeedCursor(long sortKey, long id) {

    /**
     * 游标版本（结构变更时递增，旧游标直接判为无效）
     */
    public static final String VERSION = "v1";

    private static final String SEPARATOR = "|";

    private static final String INVALID_CURSOR_MESSAGE = "翻页游标无效，请重新加载首屏";

    /**
     * 构造游标
     *
     * @param sortKey 排序键毫秒值
     * @param id      主键
     * @return 游标
     */
    public static FeedCursor of(long sortKey, long id) {
        return new FeedCursor(sortKey, id);
    }

    /**
     * 编码为不透明字符串
     *
     * @param sortKey 排序键毫秒值
     * @param id      主键
     * @return 游标字符串
     */
    public static String encode(long sortKey, long id) {
        String plain = VERSION + SEPARATOR + sortKey + SEPARATOR + id;
        return Base64.encodeUrlSafe(plain.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解码游标
     *
     * @param cursor 游标字符串（为空表示首屏）
     * @return 游标，入参为空时返回 {@code null}
     */
    public static FeedCursor decode(String cursor) {
        if (StrUtil.isBlank(cursor)) {
            return null;
        }
        String plain = StrUtil.trimToEmpty(Base64.decodeStr(StrUtil.trim(cursor)));
        String[] parts = StrUtil.splitToArray(plain, SEPARATOR);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new BusinessException(INVALID_CURSOR_MESSAGE);
        }
        try {
            return new FeedCursor(Long.parseLong(parts[1]), Long.parseLong(parts[2]));
        } catch (NumberFormatException e) {
            throw new BusinessException(INVALID_CURSOR_MESSAGE);
        }
    }

    /**
     * 时间列 → 排序键
     *
     * @param dateTime 时间
     * @return 毫秒值，入参为空时返回 0
     */
    public static long toSortKey(LocalDateTime dateTime) {
        return dateTime == null ? 0L : dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * 排序键 → 时间列（作为 SQL 边界参数）
     *
     * @param sortKey 毫秒值
     * @return 本地时间
     */
    public static LocalDateTime toLocalDateTime(long sortKey) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(sortKey), ZoneId.systemDefault());
    }
}
