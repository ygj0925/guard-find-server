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

import com.guardfind.server.tool.model.req.ToolGoalReq;
import com.guardfind.server.tool.model.req.ToolKeyResultReq;
import com.guardfind.server.tool.model.req.ToolKrProgressReq;
import com.guardfind.server.tool.model.req.ToolTaskReq;
import com.guardfind.server.tool.model.req.ToolScoreReq;
import com.guardfind.server.tool.model.req.ToolTaskStatusReq;
import com.guardfind.server.tool.model.resp.ToolGoalResp;
import com.guardfind.server.tool.model.resp.ToolKrLogResp;
import com.guardfind.server.tool.model.resp.ToolTaskResp;
import com.guardfind.server.tool.service.ToolGoalService;
import com.guardfind.server.tool.service.ToolTaskService;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 工具箱 API 第三批:任务分派(家庭组)+ 目标 OKR lite(个人)。
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Tag(name = "任务分派与目标 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/tool")
public class ToolTaskGoalController {

    private final ToolTaskService taskService;
    private final ToolGoalService goalService;

    // ==================== 任务分派 ====================

    @Operation(summary = "创建任务", description = "创建人与负责人都必须是组成员;负责人防跨组派单")
    @RateLimiter(name = "APP:TOOL:TASK", rate = 60, interval = 1, unit = TimeUnit.MINUTES, type = LimitType.IP)
    @PostMapping("/task")
    public Long createTask(@RequestBody @Valid ToolTaskReq req) {
        return taskService.create(req);
    }

    @Operation(summary = "任务列表", description = "scope=mine 指派给我的(跨组);scope=all 组内全部(须传 groupId)")
    @Parameter(name = "scope", description = "mine | all,缺省 mine")
    @Parameter(name = "groupId", description = "组 ID(scope=all 必填)")
    @GetMapping("/task/list")
    public List<ToolTaskResp> listTasks(@RequestParam(required = false, defaultValue = "mine") String scope,
                                        @RequestParam(required = false) Long groupId) {
        return taskService.list(scope, groupId);
    }

    @Operation(summary = "更新任务状态", description = "创建者或负责人可推动;1 待办 2 进行中 3 已完成")
    @Parameter(name = "id", description = "任务 ID", required = true)
    @PostMapping("/task/{id}/status")
    public void updateTaskStatus(@PathVariable Long id, @RequestBody @Valid ToolTaskStatusReq req) {
        taskService.updateStatus(id, req.getStatus());
    }

    @Operation(summary = "编辑任务", description = "仅创建者")
    @Parameter(name = "id", description = "任务 ID", required = true)
    @PutMapping("/task/{id}")
    public void updateTask(@PathVariable Long id, @RequestBody @Valid ToolTaskReq req) {
        taskService.update(id, req);
    }

    @Operation(summary = "删除任务", description = "仅创建者")
    @Parameter(name = "id", description = "任务 ID", required = true)
    @DeleteMapping("/task/{id}")
    public void deleteTask(@PathVariable Long id) {
        taskService.delete(id);
    }

    // ==================== 目标 OKR ====================

    @Operation(summary = "创建目标")
    @PostMapping("/goal")
    public Long createGoal(@RequestBody @Valid ToolGoalReq req) {
        return goalService.create(req);
    }

    @Operation(summary = "目标列表", description = "带 KR 与进度;period 可空=全部周期")
    @Parameter(name = "period", description = "yyyy 或 yyyyQn")
    @GetMapping("/goal/list")
    public List<ToolGoalResp> listGoals(@RequestParam(required = false) String period) {
        return goalService.list(period);
    }

    @Operation(summary = "修改目标", description = "仅本人")
    @Parameter(name = "id", description = "目标 ID", required = true)
    @PutMapping("/goal/{id}")
    public void updateGoal(@PathVariable Long id, @RequestBody @Valid ToolGoalReq req) {
        goalService.update(id, req);
    }

    @Operation(summary = "设置复盘评分", description = "0-1")
    @Parameter(name = "id", description = "目标 ID", required = true)
    @PutMapping("/goal/{id}/score")
    public void setScore(@PathVariable Long id, @RequestBody @Valid ToolScoreReq req) {
        goalService.setScore(id, req.getScore());
    }

    @Operation(summary = "修改 KR 元信息", description = "标题/目标值/单位/权重/信心指数")
    @Parameter(name = "krId", description = "KR ID", required = true)
    @PutMapping("/goal/kr/{krId}")
    public void updateKr(@PathVariable Long krId,
                         @RequestBody @Valid ToolKeyResultReq req,
                         @RequestParam(required = false) Integer confidence) {
        goalService.updateKr(krId, req, confidence);
    }

    @Operation(summary = "KR 时间线", description = "每次进度更新的留痕,倒序")
    @Parameter(name = "krId", description = "KR ID", required = true)
    @GetMapping("/goal/kr/{krId}/logs")
    public List<ToolKrLogResp> krLogs(@PathVariable Long krId) {
        return goalService.krLogs(krId);
    }

    @Operation(summary = "关联任务到 KR", description = "仅任务创建人或负责人可关联")
    @Parameter(name = "krId", description = "KR ID", required = true)
    @Parameter(name = "taskId", description = "任务 ID", required = true)
    @PostMapping("/goal/kr/{krId}/tasks/{taskId}")
    public void linkTask(@PathVariable Long krId, @PathVariable Long taskId) {
        goalService.linkTask(krId, taskId);
    }

    @Operation(summary = "取消任务关联")
    @Parameter(name = "krId", description = "KR ID", required = true)
    @Parameter(name = "taskId", description = "任务 ID", required = true)
    @DeleteMapping("/goal/kr/{krId}/tasks/{taskId}")
    public void unlinkTask(@PathVariable Long krId, @PathVariable Long taskId) {
        goalService.unlinkTask(krId, taskId);
    }

    @Operation(summary = "删除目标", description = "级联删除 KR")
    @Parameter(name = "id", description = "目标 ID", required = true)
    @DeleteMapping("/goal/{id}")
    public void deleteGoal(@PathVariable Long id) {
        goalService.delete(id);
    }

    @Operation(summary = "新增 KR")
    @Parameter(name = "id", description = "目标 ID", required = true)
    @PostMapping("/goal/{id}/kr")
    public Long createKr(@PathVariable Long id, @RequestBody @Valid ToolKeyResultReq req) {
        return goalService.createKr(id, req);
    }

    @Operation(summary = "更新 KR 进度", description = "当前值 ≥ 目标值自动置已完成")
    @Parameter(name = "krId", description = "KR ID", required = true)
    @PostMapping("/goal/kr/{krId}/progress")
    public void updateKrProgress(@PathVariable Long krId, @RequestBody @Valid ToolKrProgressReq req) {
        goalService.updateKrProgress(krId, req);
    }

    @Operation(summary = "标记 KR 完成/恢复", description = "手动完成时当前值抬到目标值")
    @Parameter(name = "krId", description = "KR ID", required = true)
    @PostMapping("/goal/kr/{krId}/toggle")
    public void toggleKr(@PathVariable Long krId) {
        goalService.toggleKr(krId);
    }

    @Operation(summary = "删除 KR")
    @Parameter(name = "krId", description = "KR ID", required = true)
    @DeleteMapping("/goal/kr/{krId}")
    public void deleteKr(@PathVariable Long krId) {
        goalService.deleteKr(krId);
    }
}
