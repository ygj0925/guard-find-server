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

package com.guardfind.server.budget.service;

import com.guardfind.server.budget.model.req.BudgetReq;
import com.guardfind.server.budget.model.resp.BudgetSummaryResp;

import java.util.List;

/**
 * 记账预算业务接口(C 端)。
 * 预算按自然月周期管理:总预算 + 分类预算;超 80% 预警,超 100% 标超支。
 */
public interface BudgetService {

    /** 设置/更新某月某分类预算(幂等 upsert) */
    void setBudget(Long userId, BudgetReq req);

    /** 删除某月某分类预算 */
    void removeBudget(String period, String category, Long userId);

    /** 某月预算汇总(预算 vs 实际支出对比) */
    BudgetSummaryResp summary(Long userId, String period);

    /** 某月全部预算设置 */
    List<BudgetReq> listBudgets(Long userId, String period);
}
