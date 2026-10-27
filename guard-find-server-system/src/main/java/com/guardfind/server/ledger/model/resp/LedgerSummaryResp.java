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

package com.guardfind.server.ledger.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 记账汇总响应（周报/月报/年报共用结构）
 *
 * <p>
 * {@code trend} 的粒度随周期变化：周/月为逐日、年为逐月，空档补零，
 * 客户端可以直接按序渲染条形图，不需要自己补点。
 * </p>
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Data
@Schema(description = "记账汇总响应")
public class LedgerSummaryResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "总支出（元）")
    private BigDecimal totalExpense;

    @Schema(description = "总收入（元）")
    private BigDecimal totalIncome;

    @Schema(description = "结余（收入-支出，可为负）")
    private BigDecimal balance;

    @Schema(description = "记录笔数")
    private Long recordCount;

    @Schema(description = "分类统计（按支出金额降序）")
    private List<CategoryStat> byCategory;

    @Schema(description = "趋势点（周/月逐日，年逐月）")
    private List<TrendPoint> trend;

    @Schema(description = "区间开始日期")
    private String startDate;

    @Schema(description = "区间结束日期")
    private String endDate;

    /**
     * 分类统计项
     */
    @Data
    @Schema(description = "分类统计项")
    public static class CategoryStat implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "分类码")
        private String category;

        @Schema(description = "支出金额（元）")
        private BigDecimal expense;

        @Schema(description = "收入金额（元）")
        private BigDecimal income;

        @Schema(description = "占支出的比例（0-1，4 位小数）")
        private BigDecimal ratio;
    }

    /**
     * 趋势点
     */
    @Data
    @Schema(description = "趋势点")
    public static class TrendPoint implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "标签（逐日 MM-dd / 逐月 yyyy-MM）")
        private String label;

        @Schema(description = "支出金额（元）")
        private BigDecimal expense;

        @Schema(description = "收入金额（元）")
        private BigDecimal income;
    }
}
