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

package com.guardfind.server.tool.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.hutool.core.util.StrUtil;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.ledger.service.LedgerGroupService;
import com.guardfind.server.tool.mapper.ToolGoalMapper;
import com.guardfind.server.tool.mapper.ToolKrLogMapper;
import com.guardfind.server.tool.mapper.ToolTaskMapper;
import com.guardfind.server.tool.mapper.ToolKeyResultMapper;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.tool.model.entity.ToolGoalDO;
import com.guardfind.server.tool.model.entity.ToolKrLogDO;
import com.guardfind.server.tool.model.entity.ToolTaskDO;
import com.guardfind.server.tool.model.entity.ToolKeyResultDO;
import com.guardfind.server.tool.model.req.ToolGoalReq;
import com.guardfind.server.tool.model.req.ToolKeyResultReq;
import com.guardfind.server.tool.model.req.ToolKrProgressReq;
import com.guardfind.server.tool.model.resp.ToolGoalResp;
import com.guardfind.server.tool.model.resp.ToolKrLogResp;
import com.guardfind.server.tool.service.ToolGoalService;
import com.guardfind.server.tool.support.KrProgressCalculator;
import com.guardfind.server.tool.support.KrStatusCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.starter.core.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 目标 OKR 实现(个人维度)。
 * 目标与 KR 均为本人私有;KR 进度 = current/target 封顶 1;达成自动置已完成。
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Service
@RequiredArgsConstructor
public class ToolGoalServiceImpl implements ToolGoalService {

    private final ToolGoalMapper goalMapper;
    private final ToolKrLogMapper logMapper;
    private final ToolTaskMapper taskMapper;
    private final AppUserMapper appUserMapper;
    private final ToolKeyResultMapper krMapper;

    // 预留:二期家庭共享目标时做组员校验
    private final LedgerGroupService groupService;

    @Override
    public Long create(ToolGoalReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ToolGoalDO goal = new ToolGoalDO();
        applyReq(goal, req);
        goal.setUserId(userId);
        goalMapper.insert(goal);
        return goal.getId();
    }

    @Override
    public List<ToolGoalResp> list(String period) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        List<ToolGoalDO> goals = goalMapper.selectList(Wrappers.<ToolGoalDO>lambdaQuery()
            .eq(ToolGoalDO::getUserId, userId)
            .eq(period != null && !period.isBlank(), ToolGoalDO::getPeriod, period)
            .orderByDesc(ToolGoalDO::getId));
        if (goals.isEmpty()) {
            return List.of();
        }
        List<Long> goalIds = goals.stream().map(ToolGoalDO::getId).toList();
        var krsByGoal = krMapper.selectList(Wrappers.<ToolKeyResultDO>lambdaQuery()
            .in(ToolKeyResultDO::getGoalId, goalIds))
            .stream()
            .collect(java.util.stream.Collectors.groupingBy(ToolKeyResultDO::getGoalId));
        return goals.stream().map(goal -> toResp(goal, krsByGoal.getOrDefault(goal.getId(), List.of()))).toList();
    }

    @Override
    public void update(Long id, ToolGoalReq req) {
        ToolGoalDO goal = getOwned(id);
        applyReq(goal, req);
        goalMapper.updateById(goal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ToolGoalDO goal = getOwned(id);
        goalMapper.deleteById(goal.getId());
        krMapper.delete(Wrappers.<ToolKeyResultDO>lambdaQuery().eq(ToolKeyResultDO::getGoalId, id));
    }

    @Override
    public Long createKr(Long goalId, ToolKeyResultReq req) {
        ToolGoalDO goal = getOwned(goalId);
        ToolKeyResultDO kr = new ToolKeyResultDO();
        kr.setGoalId(goal.getId());
        kr.setUserId(goal.getUserId());
        kr.setTitle(StrUtil.trim(req.getTitle()));
        kr.setTargetValue(req.getTargetValue());
        kr.setCurrentValue(BigDecimal.ZERO);
        kr.setUnit(req.getUnit());
        kr.setStatus(1);
        krMapper.insert(kr);
        return kr.getId();
    }

    @Override
    public void updateKrProgress(Long krId, ToolKrProgressReq req) {
        ToolKeyResultDO kr = getOwnedKr(krId);
        BigDecimal from = kr.getCurrentValue();
        kr.setCurrentValue(req.getCurrentValue());
        boolean achieved = KrProgressCalculator.achieved(req.getCurrentValue(), kr.getTargetValue());
        kr.setStatus(achieved ? 2 : 1);
        krMapper.updateById(kr);
        // 时间线:每次更新留痕(飞书 check-in 口径)
        ToolKrLogDO logEntry = new ToolKrLogDO();
        logEntry.setKrId(krId);
        logEntry.setUserId(kr.getUserId());
        logEntry.setFromValue(from);
        logEntry.setToValue(req.getCurrentValue());
        logEntry.setConfidence(kr.getConfidence());
        logMapper.insert(logEntry);
    }

    @Override
    public void toggleKr(Long krId) {
        ToolKeyResultDO kr = getOwnedKr(krId);
        // 手动完成时当前值抬到目标值,保证进度口径一致;恢复则不动数值只改状态
        if (kr.getStatus() != null && kr.getStatus() == 2) {
            kr.setStatus(1);
        } else {
            kr.setCurrentValue(kr.getTargetValue());
            kr.setStatus(2);
        }
        krMapper.updateById(kr);
    }

    @Override
    public void deleteKr(Long krId) {
        getOwnedKr(krId);
        krMapper.deleteById(krId);
        // 解除任务关联
        taskMapper.update(null, Wrappers.<com.guardfind.server.tool.model.entity.ToolTaskDO>update()
            .eq("kr_id", krId)
            .set("kr_id", null));
    }

    @Override
    public void updateKr(Long krId, ToolKeyResultReq req, Integer confidence) {
        ToolKeyResultDO kr = getOwnedKr(krId);
        kr.setTitle(StrUtil.trim(req.getTitle()));
        kr.setTargetValue(req.getTargetValue());
        kr.setUnit(req.getUnit());
        if (confidence != null) {
            kr.setConfidence(Math.max(1, Math.min(10, confidence)));
        }
        krMapper.updateById(kr);
    }

    @Override
    public List<ToolKrLogResp> krLogs(Long krId) {
        getOwnedKr(krId);
        List<ToolKrLogDO> logs = logMapper.selectList(Wrappers.<ToolKrLogDO>lambdaQuery()
            .eq(ToolKrLogDO::getKrId, krId)
            .orderByDesc(ToolKrLogDO::getId));
        List<Long> userIds = logs.stream().map(ToolKrLogDO::getUserId).distinct().toList();
        Map<Long, AppUserDO> users = userIds.isEmpty()
            ? Map.of()
            : appUserMapper.selectByIds(userIds)
                .stream()
                .collect(Collectors.toMap(AppUserDO::getId, Function.identity()));
        return logs.stream().map(logEntry -> {
            ToolKrLogResp resp = new ToolKrLogResp();
            resp.setId(logEntry.getId());
            resp.setFromValue(logEntry.getFromValue());
            resp.setToValue(logEntry.getToValue());
            resp.setConfidence(logEntry.getConfidence());
            resp.setNote(logEntry.getNote());
            resp.setCreateTime(logEntry.getCreateTime());
            AppUserDO user = users.get(logEntry.getUserId());
            if (user != null) {
                resp.setNickname(user.getNickname());
            }
            return resp;
        }).toList();
    }

    @Override
    public void linkTask(Long krId, Long taskId) {
        getOwnedKr(krId);
        ToolTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        // 仅任务创建人或负责人可关联(防止把别人的任务挂到自己的 KR 上)
        Long userId = AppUserContextHolder.getUserIdRequired();
        if (!Objects.equals(task.getCreatorUserId(), userId) && !Objects.equals(task.getAssigneeUserId(), userId)) {
            throw new BusinessException("只有任务的创建人或负责人可以关联");
        }
        task.setKrId(krId);
        taskMapper.updateById(task);
    }

    @Override
    public void unlinkTask(Long krId, Long taskId) {
        getOwnedKr(krId);
        ToolTaskDO task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getKrId(), krId)) {
            throw new BusinessException("任务与该 KR 无关联");
        }
        task.setKrId(null);
        taskMapper.updateById(task);
    }

    @Override
    public void setScore(Long id, BigDecimal score) {
        ToolGoalDO goal = getOwned(id);
        if (score == null || score.signum() < 0 || score.doubleValue() > 1) {
            throw new BusinessException("评分取值 0-1");
        }
        goal.setScore(score.setScale(2, RoundingMode.HALF_UP));
        goalMapper.updateById(goal);
    }

    private static int rank(String status) {
        return switch (status) {
            case KrStatusCalculator.BEHIND -> 0;
            case KrStatusCalculator.AT_RISK -> 1;
            case KrStatusCalculator.ON_TRACK -> 2;
            default -> 3;
        };
    }

    private void applyReq(ToolGoalDO goal, ToolGoalReq req) {
        if (StrUtil.isBlank(req.getTitle())) {
            throw new BusinessException("目标描述不能为空");
        }
        goal.setTitle(StrUtil.trim(req.getTitle()));
        goal.setPeriod(StrUtil.trim(req.getPeriod()));
    }

    private ToolGoalDO getOwned(Long id) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        if (id == null) {
            throw new BusinessException("目标 ID 不能为空");
        }
        ToolGoalDO goal = goalMapper.selectById(id);
        if (goal == null || !goal.getUserId().equals(userId)) {
            throw new BusinessException("目标不存在");
        }
        return goal;
    }

    private ToolKeyResultDO getOwnedKr(Long id) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        if (id == null) {
            throw new BusinessException("KR ID 不能为空");
        }
        ToolKeyResultDO kr = krMapper.selectById(id);
        if (kr == null || !kr.getUserId().equals(userId)) {
            throw new BusinessException("关键结果不存在");
        }
        return kr;
    }

    private ToolGoalResp toResp(ToolGoalDO goal, List<ToolKeyResultDO> krs) {
        ToolGoalResp resp = new ToolGoalResp();
        resp.setId(goal.getId());
        resp.setTitle(goal.getTitle());
        resp.setPeriod(goal.getPeriod());
        List<ToolGoalResp.KeyResult> items = krs.stream().map(kr -> {
            ToolGoalResp.KeyResult item = new ToolGoalResp.KeyResult();
            item.setId(kr.getId());
            item.setTitle(kr.getTitle());
            item.setTargetValue(kr.getTargetValue());
            item.setCurrentValue(kr.getCurrentValue());
            item.setUnit(kr.getUnit());
            item.setStatus(kr.getStatus());
            item.setWeight(kr.getWeight() == null ? 1 : kr.getWeight());
            item.setConfidence(kr.getConfidence());
            item.setProgress(KrProgressCalculator.percent(kr.getCurrentValue(), kr.getTargetValue()));
            // 关联任务进度
            List<ToolTaskDO> linked = taskMapper.selectList(Wrappers.<ToolTaskDO>lambdaQuery()
                .eq(ToolTaskDO::getKrId, kr.getId()));
            item.setTaskTotal((long)linked.size());
            item.setTaskDone(linked.stream().filter(t -> t.getStatus() != null && t.getStatus() == 3).count());
            item.setKrStatus(KrStatusCalculator.status(KrStatusCalculator.periodRange(goal
                .getPeriod())[0], KrStatusCalculator.periodRange(goal.getPeriod())[1], item.getProgress()
                    .doubleValue(), item.getStatus() == 2));
            return item;
        }).toList();
        resp.setKeyResults(items);
        // 整体进度 = ΣKR进度×权重 / Σ权重(无权重时权重=1);无 KR 记 0
        if (items.isEmpty()) {
            resp.setProgress(BigDecimal.ZERO);
        } else {
            BigDecimal weightedSum = BigDecimal.ZERO;
            BigDecimal weightSum = BigDecimal.ZERO;
            for (ToolGoalResp.KeyResult item : items) {
                BigDecimal weight = BigDecimal.valueOf(item.getWeight() == null ? 1 : item.getWeight());
                weightedSum = weightedSum.add(item.getProgress().multiply(weight));
                weightSum = weightSum.add(weight);
            }
            resp.setProgress(weightedSum.divide(weightSum, 4, RoundingMode.HALF_UP));
        }
        // 整体状态 = 最落后的 KR 状态
        resp.setGoalStatus(items.stream()
            .map(ToolGoalResp.KeyResult::getKrStatus)
            .min((a, b) -> rank(a) - rank(b))
            .orElse(KrStatusCalculator.ON_TRACK));
        return resp;
    }
}
