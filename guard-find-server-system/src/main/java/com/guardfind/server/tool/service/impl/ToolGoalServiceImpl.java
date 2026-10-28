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
import com.guardfind.server.ledger.service.LedgerGroupService;
import com.guardfind.server.tool.mapper.ToolGoalMapper;
import com.guardfind.server.tool.mapper.ToolKeyResultMapper;
import com.guardfind.server.tool.model.entity.ToolGoalDO;
import com.guardfind.server.tool.model.entity.ToolKeyResultDO;
import com.guardfind.server.tool.model.req.ToolGoalReq;
import com.guardfind.server.tool.model.req.ToolKeyResultReq;
import com.guardfind.server.tool.model.req.ToolKrProgressReq;
import com.guardfind.server.tool.model.resp.ToolGoalResp;
import com.guardfind.server.tool.service.ToolGoalService;
import com.guardfind.server.tool.support.KrProgressCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.starter.core.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

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
        kr.setCurrentValue(req.getCurrentValue());
        kr.setStatus(KrProgressCalculator.achieved(req.getCurrentValue(), kr.getTargetValue()) ? 2 : 1);
        krMapper.updateById(kr);
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

    private static ToolGoalResp toResp(ToolGoalDO goal, List<ToolKeyResultDO> krs) {
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
            item.setProgress(KrProgressCalculator.percent(kr.getCurrentValue(), kr.getTargetValue()));
            return item;
        }).toList();
        resp.setKeyResults(items);
        // 整体进度 = 各 KR 进度均值(无 KR 记 0)
        if (items.isEmpty()) {
            resp.setProgress(BigDecimal.ZERO);
        } else {
            BigDecimal sum = items.stream()
                .map(ToolGoalResp.KeyResult::getProgress)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            resp.setProgress(sum.divide(BigDecimal.valueOf(items.size()), 4, RoundingMode.HALF_UP));
        }
        return resp;
    }
}
