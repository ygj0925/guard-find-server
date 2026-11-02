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

package com.guardfind.server.budget.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guardfind.server.budget.mapper.BudgetMapper;
import com.guardfind.server.budget.model.entity.BudgetDO;
import com.guardfind.server.budget.model.req.BudgetReq;
import com.guardfind.server.budget.model.resp.BudgetSummaryResp;
import com.guardfind.server.budget.service.BudgetService;
import com.guardfind.server.ledger.mapper.LedgerRecordMapper;
import com.guardfind.server.ledger.model.entity.LedgerRecordDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 记账预算实现。
 * 汇总口径:某自然月内,按分类统计实际支出,与预算对比;
 * ratio ≥ 80% → warn,≥ 100% → over;总预算与分类预算独立展示。
 */
@Service
@RequiredArgsConstructor
public class BudgetServiceImpl implements BudgetService {

    private final BudgetMapper budgetMapper;
    private final LedgerRecordMapper ledgerRecordMapper;

    @Override
    public void setBudget(Long userId, BudgetReq req) {
        BudgetDO existing = budgetMapper.selectOne(Wrappers.<BudgetDO>lambdaQuery()
            .eq(BudgetDO::getUserId, userId)
            .eq(BudgetDO::getPeriod, req.getPeriod())
            .eq(BudgetDO::getCategory, req.getCategory())
            .last("LIMIT 1"));
        if (existing != null) {
            existing.setBudgetAmount(req.getBudgetAmount());
            budgetMapper.updateById(existing);
        } else {
            BudgetDO budget = new BudgetDO();
            budget.setUserId(userId);
            budget.setPeriod(req.getPeriod());
            budget.setCategory(req.getCategory());
            budget.setBudgetAmount(req.getBudgetAmount());
            budgetMapper.insert(budget);
        }
    }

    @Override
    public void removeBudget(String period, String category, Long userId) {
        budgetMapper.delete(Wrappers.<BudgetDO>lambdaQuery()
            .eq(BudgetDO::getUserId, userId)
            .eq(BudgetDO::getPeriod, period)
            .eq(BudgetDO::getCategory, category));
    }

    @Override
    public BudgetSummaryResp summary(Long userId, String period) {
        YearMonth month = YearMonth.parse(period);
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        // 当月实际支出(按分类分组)
        List<LedgerRecordDO> expenses = ledgerRecordMapper.selectList(Wrappers.<LedgerRecordDO>lambdaQuery()
            .eq(LedgerRecordDO::getUserId, userId)
            .eq(LedgerRecordDO::getType, 1)
            .ge(LedgerRecordDO::getRecordDate, start)
            .le(LedgerRecordDO::getRecordDate, end));
        Map<String, BigDecimal> spentByCategory = expenses.stream()
            .collect(Collectors.groupingBy(LedgerRecordDO::getCategory, Collectors
                .reducing(BigDecimal.ZERO, LedgerRecordDO::getAmount, BigDecimal::add)));
        BigDecimal totalSpent = spentByCategory.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        // 预算设置
        List<BudgetDO> budgets = budgetMapper.selectList(Wrappers.<BudgetDO>lambdaQuery()
            .eq(BudgetDO::getUserId, userId)
            .eq(BudgetDO::getPeriod, period));

        BudgetSummaryResp resp = new BudgetSummaryResp();
        resp.setPeriod(period);

        // 总预算
        BudgetDO totalBudget = budgets.stream().filter(b -> "total".equals(b.getCategory())).findFirst().orElse(null);
        BigDecimal totalBudgetAmount = totalBudget != null ? totalBudget.getBudgetAmount() : BigDecimal.ZERO;
        resp.setTotalBudget(totalBudgetAmount);
        resp.setTotalSpent(totalSpent);
        resp.setTotalRemaining(totalBudgetAmount.subtract(totalSpent));

        // 分类预算(排除 total)
        List<BudgetDO> categoryBudgets = budgets.stream()
            .filter(b -> !"total".equals(b.getCategory()))
            .collect(Collectors.toList());

        List<BudgetSummaryResp.CategoryBudget> categoryList = new ArrayList<>();
        // 已设置预算的分类
        for (BudgetDO cb : categoryBudgets) {
            BudgetSummaryResp.CategoryBudget item = new BudgetSummaryResp.CategoryBudget();
            item.setCategory(cb.getCategory());
            item.setBudgetAmount(cb.getBudgetAmount());
            BigDecimal spent = spentByCategory.getOrDefault(cb.getCategory(), BigDecimal.ZERO);
            item.setSpent(spent);
            item.setRemaining(cb.getBudgetAmount().subtract(spent));
            BigDecimal ratio = cb.getBudgetAmount().signum() > 0
                ? spent.divide(cb.getBudgetAmount(), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            item.setRatio(ratio);
            item.setAlertLevel(alertLevel(ratio));
            categoryList.add(item);
        }
        // 有支出但未设预算的分类(补展示)
        for (Map.Entry<String, BigDecimal> entry : spentByCategory.entrySet()) {
            boolean hasBudget = categoryBudgets.stream().anyMatch(cb -> cb.getCategory().equals(entry.getKey()));
            if (!hasBudget && entry.getValue().signum() > 0) {
                BudgetSummaryResp.CategoryBudget item = new BudgetSummaryResp.CategoryBudget();
                item.setCategory(entry.getKey());
                item.setBudgetAmount(BigDecimal.ZERO);
                item.setSpent(entry.getValue());
                item.setRemaining(entry.getValue().negate());
                item.setRatio(BigDecimal.ONE);
                item.setAlertLevel("over");
                categoryList.add(item);
            }
        }
        resp.setCategories(categoryList);
        return resp;
    }

    @Override
    public List<BudgetReq> listBudgets(Long userId, String period) {
        List<BudgetDO> budgets = budgetMapper.selectList(Wrappers.<BudgetDO>lambdaQuery()
            .eq(BudgetDO::getUserId, userId)
            .eq(BudgetDO::getPeriod, period));
        return budgets.stream().map(b -> {
            BudgetReq req = new BudgetReq();
            req.setPeriod(b.getPeriod());
            req.setCategory(b.getCategory());
            req.setBudgetAmount(b.getBudgetAmount());
            return req;
        }).toList();
    }

    private String alertLevel(BigDecimal ratio) {
        if (ratio.doubleValue() >= 1.0)
            return "over";
        if (ratio.doubleValue() >= 0.8)
            return "warn";
        return "normal";
    }
}
