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

package com.guardfind.server.budget.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.budget.model.req.BudgetReq;
import com.guardfind.server.budget.model.resp.BudgetSummaryResp;
import com.guardfind.server.budget.service.BudgetService;

import java.util.List;

/**
 * 记账预算 API(C 端)。预算 vs 实际支出对比,超 80% 预警,超 100% 标超支。
 */
@Tag(name = "记账预算 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/budget")
public class BudgetController {

    private final BudgetService budgetService;

    @Operation(summary = "设置/更新预算", description = "幂等 upsert;period=yyyy-MM;category=total 或分类码")
    @PostMapping("/set")
    public void setBudget(@RequestBody @Valid BudgetReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        budgetService.setBudget(userId, req);
    }

    @Operation(summary = "删除预算")
    @Parameter(name = "period", description = "yyyy-MM", required = true)
    @Parameter(name = "category", description = "分类码", required = true)
    @DeleteMapping("/remove")
    public void removeBudget(@RequestParam @NotBlank @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$") String period,
                             @RequestParam @NotBlank String category) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        budgetService.removeBudget(period, category, userId);
    }

    @Operation(summary = "预算汇总", description = "预算 vs 实际支出对比,含分类明细")
    @Parameter(name = "period", description = "yyyy-MM", required = true)
    @GetMapping("/summary")
    public BudgetSummaryResp summary(@RequestParam @NotBlank @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$") String period) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        return budgetService.summary(userId, period);
    }

    @Operation(summary = "我的预算设置列表")
    @Parameter(name = "period", description = "yyyy-MM", required = true)
    @GetMapping("/list")
    public List<BudgetReq> listBudgets(@RequestParam @NotBlank @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$") String period) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        return budgetService.listBudgets(userId, period);
    }
}
