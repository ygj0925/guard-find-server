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

package com.guardfind.server.budget.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "预算汇总响应")
public class BudgetSummaryResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String period;

    private BigDecimal totalBudget;
    private BigDecimal totalSpent;
    private BigDecimal totalRemaining;

    private List<CategoryBudget> categories;

    @Data
    @Schema(description = "分类预算条目")
    public static class CategoryBudget implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String category;
        private BigDecimal budgetAmount;
        private BigDecimal spent;
        private BigDecimal remaining;
        /** 支出占比(0-1,>1 表示超支)。 */
        private BigDecimal ratio;
        /** normal/warn/over:正常/接近超支(>80%)/已超支。 */
        private String alertLevel;
    }
}
