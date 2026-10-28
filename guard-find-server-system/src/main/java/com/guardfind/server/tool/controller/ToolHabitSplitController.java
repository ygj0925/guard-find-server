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

package com.guardfind.server.tool.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.starter.ratelimiter.annotation.RateLimiter;
import top.continew.starter.ratelimiter.enums.LimitType;

import com.guardfind.server.tool.model.req.ToolHabitReq;
import com.guardfind.server.tool.model.req.ToolSplitReq;
import com.guardfind.server.tool.model.resp.ToolHabitResp;
import com.guardfind.server.tool.model.resp.ToolSplitResp;
import com.guardfind.server.tool.service.ToolHabitService;
import com.guardfind.server.tool.service.ToolSplitService;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 工具箱小工具 API 第二批:习惯打卡 + AA 分账。
 * 登录态由 AppUserContextHolder 判定;均为个人数据。
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Tag(name = "工具箱小工具 API(打卡/分账)")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/tool")
public class ToolHabitSplitController {

    private final ToolHabitService habitService;
    private final ToolSplitService splitService;

    // ==================== 习惯打卡 ====================

    @Operation(summary = "新建习惯")
    @PostMapping("/habit")
    public Long createHabit(@RequestBody @Valid ToolHabitReq req) {
        return habitService.create(req);
    }

    @Operation(summary = "修改习惯", description = "仅本人记录可修改")
    @Parameter(name = "id", description = "习惯 ID", required = true)
    @PutMapping("/habit/{id}")
    public void updateHabit(@PathVariable Long id, @RequestBody @Valid ToolHabitReq req) {
        habitService.update(id, req);
    }

    @Operation(summary = "删除习惯", description = "级联删除打卡流水")
    @Parameter(name = "id", description = "习惯 ID", required = true)
    @DeleteMapping("/habit/{id}")
    public void deleteHabit(@PathVariable Long id) {
        habitService.delete(id);
    }

    @Operation(summary = "习惯列表", description = "带 todayChecked/currentStreak/bestStreak/totalDays 统计")
    @GetMapping("/habit/list")
    public List<ToolHabitResp> listHabits() {
        return habitService.list();
    }

    @Operation(summary = "打卡/取消今天", description = "幂等翻转;返回 true=已打卡")
    @Parameter(name = "id", description = "习惯 ID", required = true)
    @PostMapping("/habit/{id}/checkin")
    public Boolean checkin(@PathVariable Long id) {
        return habitService.toggleToday(id);
    }

    @Operation(summary = "某月打卡日期", description = "月历网格用;month 缺省当月")
    @Parameter(name = "id", description = "习惯 ID", required = true)
    @Parameter(name = "month", description = "yyyy-MM")
    @GetMapping("/habit/{id}/log")
    public List<String> monthLog(@PathVariable Long id, @RequestParam(required = false) String month) {
        return habitService.monthLog(id, month);
    }

    // ==================== AA 分账 ====================

    @Operation(summary = "保存分账记录")
    @PostMapping("/split")
    public Long createSplit(@RequestBody @Valid ToolSplitReq req) {
        return splitService.create(req);
    }

    @Operation(summary = "分账历史", description = "按日期倒序;perHead/remainderCents 服务端算好")
    @GetMapping("/split/list")
    public List<ToolSplitResp> listSplits() {
        return splitService.list();
    }

    @Operation(summary = "删除分账记录")
    @Parameter(name = "id", description = "记录 ID", required = true)
    @DeleteMapping("/split/{id}")
    public void deleteSplit(@PathVariable Long id) {
        splitService.delete(id);
    }

    @Operation(summary = "标记已写回记账", description = "幂等")
    @Parameter(name = "id", description = "记录 ID", required = true)
    @RateLimiter(name = "APP:TOOL:WB", rate = 30, interval = 1, unit = TimeUnit.MINUTES, type = LimitType.IP)
    @PostMapping("/split/{id}/written-back")
    public void markWrittenBack(@PathVariable Long id) {
        splitService.markWrittenBack(id);
    }
}
