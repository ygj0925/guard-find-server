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
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.exception.AppBizException;
import com.guardfind.server.tool.mapper.ToolCountdownMapper;
import com.guardfind.server.tool.model.entity.ToolCountdownDO;
import com.guardfind.server.tool.model.req.ToolCountdownReq;
import com.guardfind.server.tool.model.resp.ToolCountdownResp;
import com.guardfind.server.tool.service.ToolCountdownService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.starter.core.exception.BusinessException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 倒数日实现。个人数据全量查询(单用户条目量级小),daysLeft 在服务端算一次,
 * 三端展示口径一致,不在客户端重复实现。
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Service
@RequiredArgsConstructor
public class ToolCountdownServiceImpl implements ToolCountdownService {

    private final ToolCountdownMapper countdownMapper;

    @Override
    public Long create(ToolCountdownReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ToolCountdownDO record = new ToolCountdownDO();
        applyReq(record, req);
        record.setUserId(userId);
        countdownMapper.insert(record);
        return record.getId();
    }

    @Override
    public void update(Long id, ToolCountdownReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ToolCountdownDO record = getOwned(id, userId);
        applyReq(record, req);
        countdownMapper.updateById(record);
    }

    @Override
    public void delete(Long id) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        countdownMapper.deleteById(getOwned(id, userId).getId());
    }

    @Override
    public List<ToolCountdownResp> list() {
        Long userId = AppUserContextHolder.getUserIdRequired();
        LocalDate today = LocalDate.now();
        return countdownMapper.selectList(Wrappers.<ToolCountdownDO>lambdaQuery()
            .eq(ToolCountdownDO::getUserId, userId)
            .orderByAsc(ToolCountdownDO::getTargetDate)).stream().map(record -> toResp(record, today)).toList();
    }

    private void applyReq(ToolCountdownDO record, ToolCountdownReq req) {
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new BusinessException("事项名称不能为空");
        }
        record.setTitle(req.getTitle().trim());
        record.setTargetDate(req.getTargetDate());
        record.setRemark(req.getRemark());
    }

    private ToolCountdownDO getOwned(Long id, Long userId) {
        if (id == null) {
            throw new BusinessException("记录 ID 不能为空");
        }
        ToolCountdownDO record = countdownMapper.selectById(id);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new AppBizException(AppBizException.NOT_FOUND, "倒数日不存在");
        }
        return record;
    }

    private static ToolCountdownResp toResp(ToolCountdownDO record, LocalDate today) {
        ToolCountdownResp resp = new ToolCountdownResp();
        resp.setId(record.getId());
        resp.setTitle(record.getTitle());
        resp.setTargetDate(record.getTargetDate());
        resp.setRemark(record.getRemark());
        resp.setDaysLeft(ChronoUnit.DAYS.between(today, record.getTargetDate()));
        return resp;
    }
}
