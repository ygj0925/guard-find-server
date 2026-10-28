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
import com.guardfind.server.tool.mapper.ToolSplitMapper;
import com.guardfind.server.tool.model.entity.ToolSplitDO;
import com.guardfind.server.tool.model.req.ToolSplitReq;
import com.guardfind.server.tool.model.resp.ToolSplitResp;
import com.guardfind.server.tool.service.ToolSplitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.starter.core.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * AA 分账实现。每人金额口径:总分「分」÷人数 向下取整,余数(分)由前 N 人各多承担 1 分,
 * 保证每人相加恰好等于总额;响应里 perHead=基准值、remainderCents=余数。
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Service
@RequiredArgsConstructor
public class ToolSplitServiceImpl implements ToolSplitService {

    private final ToolSplitMapper splitMapper;

    @Override
    public Long create(ToolSplitReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        validateAmount(req);
        ToolSplitDO record = new ToolSplitDO();
        record.setUserId(userId);
        record.setTitle(StrUtil.blankToDefault(StrUtil.trim(req.getTitle()), "AA 分账"));
        record.setTotalAmount(req.getTotalAmount());
        record.setPeopleCount(req.getPeopleCount());
        record.setSplitDate(req.getSplitDate());
        record.setRemark(req.getRemark());
        record.setWrittenBack(0);
        splitMapper.insert(record);
        return record.getId();
    }

    @Override
    public List<ToolSplitResp> list() {
        Long userId = AppUserContextHolder.getUserIdRequired();
        return splitMapper.selectList(Wrappers.<ToolSplitDO>lambdaQuery()
            .eq(ToolSplitDO::getUserId, userId)
            .orderByDesc(ToolSplitDO::getSplitDate)
            .orderByDesc(ToolSplitDO::getId)).stream().map(ToolSplitServiceImpl::toResp).toList();
    }

    @Override
    public void delete(Long id) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        splitMapper.deleteById(getOwned(id, userId).getId());
    }

    @Override
    public void markWrittenBack(Long id) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ToolSplitDO record = getOwned(id, userId);
        record.setWrittenBack(1);
        splitMapper.updateById(record);
    }

    private void validateAmount(ToolSplitReq req) {
        if (req.getPeopleCount() == null || req.getPeopleCount() < 1) {
            throw new BusinessException("参与人数至少 1 人");
        }
        if (req.getTotalAmount() == null || req.getTotalAmount().signum() <= 0) {
            throw new BusinessException("总金额必须大于 0");
        }
        if (req.getSplitDate() == null) {
            throw new BusinessException("分账日期不能为空");
        }
    }

    private ToolSplitDO getOwned(Long id, Long userId) {
        if (id == null) {
            throw new BusinessException("记录 ID 不能为空");
        }
        ToolSplitDO record = splitMapper.selectById(id);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new AppBizException(AppBizException.NOT_FOUND, "分账记录不存在");
        }
        return record;
    }

    private static ToolSplitResp toResp(ToolSplitDO record) {
        ToolSplitResp resp = new ToolSplitResp();
        resp.setId(record.getId());
        resp.setTitle(record.getTitle());
        resp.setTotalAmount(record.getTotalAmount());
        resp.setPeopleCount(record.getPeopleCount());
        long totalCents = record.getTotalAmount().movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
        int people = record.getPeopleCount();
        long headCents = totalCents / people;
        resp.setPerHead(BigDecimal.valueOf(headCents, 2));
        resp.setRemainderCents(totalCents % people);
        resp.setSplitDate(record.getSplitDate());
        resp.setRemark(record.getRemark());
        resp.setWrittenBack(record.getWrittenBack() != null && record.getWrittenBack() == 1);
        return resp;
    }
}
