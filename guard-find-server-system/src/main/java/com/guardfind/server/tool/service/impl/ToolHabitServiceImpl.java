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

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.exception.AppBizException;
import com.guardfind.server.tool.mapper.ToolHabitLogMapper;
import com.guardfind.server.tool.mapper.ToolHabitMapper;
import com.guardfind.server.tool.model.entity.ToolHabitDO;
import com.guardfind.server.tool.model.entity.ToolHabitLogDO;
import com.guardfind.server.tool.model.req.ToolHabitReq;
import com.guardfind.server.tool.model.resp.ToolHabitResp;
import com.guardfind.server.tool.service.ToolHabitService;
import com.guardfind.server.tool.support.StreakCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.starter.core.exception.BusinessException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 习惯打卡实现。streak 统计在内存做(单用户习惯×流水量级小),
 * 口径统一走 StreakCalculator,客户端不重复实现。
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Service
@RequiredArgsConstructor
public class ToolHabitServiceImpl implements ToolHabitService {

    private final ToolHabitMapper habitMapper;
    private final ToolHabitLogMapper logMapper;

    @Override
    public Long create(ToolHabitReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ToolHabitDO habit = new ToolHabitDO();
        applyReq(habit, req);
        habit.setUserId(userId);
        habitMapper.insert(habit);
        return habit.getId();
    }

    @Override
    public void update(Long id, ToolHabitReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ToolHabitDO habit = getOwned(id, userId);
        applyReq(habit, req);
        habitMapper.updateById(habit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ToolHabitDO habit = getOwned(id, userId);
        habitMapper.deleteById(habit.getId());
        logMapper.delete(Wrappers.<ToolHabitLogDO>lambdaQuery().eq(ToolHabitLogDO::getHabitId, id));
    }

    @Override
    public List<ToolHabitResp> list() {
        Long userId = AppUserContextHolder.getUserIdRequired();
        LocalDate today = LocalDate.now();
        List<ToolHabitDO> habits = habitMapper.selectList(Wrappers.<ToolHabitDO>lambdaQuery()
            .eq(ToolHabitDO::getUserId, userId)
            .orderByDesc(ToolHabitDO::getId));
        if (habits.isEmpty()) {
            return List.of();
        }
        List<Long> habitIds = habits.stream().map(ToolHabitDO::getId).toList();
        // 一次把全部流水拉出来按习惯分组,避免 N+1
        var logsByHabit = logMapper.selectList(Wrappers.<ToolHabitLogDO>lambdaQuery()
            .in(ToolHabitLogDO::getHabitId, habitIds))
            .stream()
            .collect(java.util.stream.Collectors.groupingBy(ToolHabitLogDO::getHabitId, java.util.stream.Collectors
                .mapping(ToolHabitLogDO::getCheckinDate, java.util.stream.Collectors.toList())));
        return habits.stream()
            .map(habit -> toResp(habit, logsByHabit.getOrDefault(habit.getId(), List.of()), today))
            .toList();
    }

    @Override
    public boolean toggleToday(Long id) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        getOwned(id, userId);
        LocalDate today = LocalDate.now();
        ToolHabitLogDO existing = logMapper.selectOne(Wrappers.<ToolHabitLogDO>lambdaQuery()
            .eq(ToolHabitLogDO::getHabitId, id)
            .eq(ToolHabitLogDO::getCheckinDate, today)
            .last("LIMIT 1"));
        if (existing != null) {
            logMapper.deleteById(existing.getId());
            return false;
        }
        ToolHabitLogDO log = new ToolHabitLogDO();
        log.setHabitId(id);
        log.setUserId(userId);
        log.setCheckinDate(today);
        logMapper.insert(log);
        return true;
    }

    @Override
    public List<String> monthLog(Long id, String month) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        getOwned(id, userId);
        YearMonth parsed;
        try {
            parsed = YearMonth.parse(StrUtil.blankToDefault(month, LocalDate.now().toString().substring(0, 7)));
        } catch (DateTimeParseException e) {
            throw new BusinessException("月份格式应为 yyyy-MM");
        }
        return logMapper.selectList(Wrappers.<ToolHabitLogDO>lambdaQuery()
            .eq(ToolHabitLogDO::getHabitId, id)
            .ge(ToolHabitLogDO::getCheckinDate, parsed.atDay(1))
            .le(ToolHabitLogDO::getCheckinDate, parsed.atEndOfMonth()))
            .stream()
            .map(log -> log.getCheckinDate().toString())
            .sorted()
            .toList();
    }

    private void applyReq(ToolHabitDO habit, ToolHabitReq req) {
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new BusinessException("习惯名称不能为空");
        }
        habit.setTitle(req.getTitle().trim());
        habit.setRemark(req.getRemark());
    }

    private ToolHabitDO getOwned(Long id, Long userId) {
        if (id == null) {
            throw new BusinessException("习惯 ID 不能为空");
        }
        ToolHabitDO habit = habitMapper.selectById(id);
        if (habit == null || !habit.getUserId().equals(userId)) {
            throw new AppBizException(AppBizException.NOT_FOUND, "习惯不存在");
        }
        return habit;
    }

    private static ToolHabitResp toResp(ToolHabitDO habit, List<LocalDate> checkins, LocalDate today) {
        long[] stats = StreakCalculator.calculate(checkins, today);
        ToolHabitResp resp = new ToolHabitResp();
        resp.setId(habit.getId());
        resp.setTitle(habit.getTitle());
        resp.setRemark(habit.getRemark());
        resp.setTodayChecked(checkins.contains(today));
        resp.setCurrentStreak(stats[0]);
        resp.setBestStreak(stats[1]);
        resp.setTotalDays(stats[2]);
        return resp;
    }
}
