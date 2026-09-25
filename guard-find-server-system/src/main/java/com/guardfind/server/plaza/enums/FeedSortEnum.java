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

package com.guardfind.server.plaza.enums;

import cn.hutool.core.util.StrUtil;

/**
 * 广场信息流排序方式（四种流互斥，状态过滤由服务端强制）
 *
 * <p>
 * 每条流给出：用于排序与游标的"时间列表达式"（SQL 表达式，与查询里的 COALESCE 保持一致），
 * 保证游标分页在并发写入下仍稳定。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public enum FeedSortEnum {

    /**
     * 最新：按发布时间倒序（未发布时间的历史数据回退到创建时间）
     */
    LATEST("latest", "COALESCE(publish_time, create_time)") {
        @Override
        public boolean includeFound() {
            return false;
        }
    },

    /**
     * 紧急：黄金 72 小时内的已发布档案，按发布时间倒序
     */
    URGENT("urgent", "COALESCE(publish_time, create_time)") {
        @Override
        public boolean includeFound() {
            return false;
        }
    },

    /**
     * 就近：按失踪地区划（省/市）过滤后按发布时间倒序（一期不采集经纬度，用行政区近似"就近"）
     */
    NEARBY("nearby", "COALESCE(publish_time, create_time)") {
        @Override
        public boolean includeFound() {
            return false;
        }
    },

    /**
     * 喜报：已找回档案，按找回时间倒序
     */
    FOUND("found", "COALESCE(found_time, update_time, create_time)") {
        @Override
        public boolean includeFound() {
            return true;
        }
    };

    private final String value;
    private final String timeColumnExpr;

    FeedSortEnum(String value, String timeColumnExpr) {
        this.value = value;
        this.timeColumnExpr = timeColumnExpr;
    }

    public String getValue() {
        return value;
    }

    public String getTimeColumnExpr() {
        return timeColumnExpr;
    }

    /**
     * 该流是否展示"已找回"档案（其余流一律排除，避免喜报混入寻人列表）
     *
     * @return true：喜报流
     */
    public abstract boolean includeFound();

    /**
     * 解析排序方式（大小写不敏感）
     *
     * @param value 排序值
     * @return 排序方式，未匹配时为 null
     */
    public static FeedSortEnum of(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        for (FeedSortEnum item : values()) {
            if (item.value.equalsIgnoreCase(StrUtil.trim(value))) {
                return item;
            }
        }
        return null;
    }
}
