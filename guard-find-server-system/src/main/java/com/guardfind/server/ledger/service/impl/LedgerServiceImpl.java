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

package com.guardfind.server.ledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.exception.AppBizException;
import com.guardfind.server.ledger.enums.LedgerTypeEnum;
import com.guardfind.server.ledger.mapper.LedgerRecordMapper;
import com.guardfind.server.ledger.model.entity.LedgerRecordDO;
import com.guardfind.server.ledger.model.req.LedgerRecordReq;
import com.guardfind.server.ledger.model.resp.LedgerPageResp;
import com.guardfind.server.ledger.model.resp.LedgerRecordResp;
import com.guardfind.server.ledger.model.resp.LedgerSummaryResp;
import com.guardfind.server.ledger.service.LedgerService;
import com.guardfind.server.ledger.support.LedgerCategories;
import com.guardfind.server.ledger.support.LedgerSummaryCalculator;
import com.guardfind.server.ledger.support.PeriodParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.query.PageQuery;
import cn.hutool.core.util.StrUtil;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 记账业务实现
 *
 * <p>
 * 登录态由 {@code AppUserContextHolder} 判定；个人数据一律带 userId 过滤，
 * 修改/删除前校验归属，越权与不存在同样返回"记录不存在"，避免探测他人数据。
 * </p>
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Service
@RequiredArgsConstructor
public class LedgerServiceImpl implements LedgerService {

    private final LedgerRecordMapper ledgerRecordMapper;

    @Override
    public Long create(LedgerRecordReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        LedgerTypeEnum type = LedgerTypeEnum.of(req.getType());
        validateCategory(type, req.getCategory());
        LedgerRecordDO record = new LedgerRecordDO();
        record.setUserId(userId);
        record.setType(req.getType());
        record.setAmount(req.getAmount());
        record.setCategory(req.getCategory());
        record.setRecordDate(req.getRecordDate());
        record.setRemark(req.getRemark());
        ledgerRecordMapper.insert(record);
        return record.getId();
    }

    @Override
    public void update(Long id, LedgerRecordReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        LedgerRecordDO record = getOwnedRecord(id, userId);
        LedgerTypeEnum type = LedgerTypeEnum.of(req.getType());
        validateCategory(type, req.getCategory());
        record.setType(req.getType());
        record.setAmount(req.getAmount());
        record.setCategory(req.getCategory());
        record.setRecordDate(req.getRecordDate());
        record.setRemark(req.getRemark());
        ledgerRecordMapper.updateById(record);
    }

    @Override
    public void delete(Long id) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        LedgerRecordDO record = getOwnedRecord(id, userId);
        ledgerRecordMapper.deleteById(record.getId());
    }

    @Override
    public LedgerPageResp page(String month, PageQuery pageQuery) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        LambdaQueryWrapper<LedgerRecordDO> wrapper = Wrappers.<LedgerRecordDO>lambdaQuery()
            .eq(LedgerRecordDO::getUserId, userId);
        if (StrUtil.isNotBlank(month)) {
            YearMonth parsed = parseMonth(month);
            wrapper.ge(LedgerRecordDO::getRecordDate, parsed.atDay(1))
                .le(LedgerRecordDO::getRecordDate, parsed.atEndOfMonth());
        }
        wrapper.orderByDesc(LedgerRecordDO::getRecordDate).orderByDesc(LedgerRecordDO::getId);
        Page<LedgerRecordDO> page = ledgerRecordMapper
            .selectPage(new Page<>(page(pageQuery), size(pageQuery)), wrapper);
        List<LedgerRecordResp> list = page.getRecords().stream().map(LedgerServiceImpl::toResp).toList();
        return LedgerPageResp.of(list, page.getTotal());
    }

    @Override
    public LedgerSummaryResp summary(String period, String date) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        LedgerSummaryCalculator.PeriodRange range = PeriodParser.parse(period, date);
        List<LedgerRecordDO> records = ledgerRecordMapper.selectList(Wrappers.<LedgerRecordDO>lambdaQuery()
            .eq(LedgerRecordDO::getUserId, userId)
            .ge(LedgerRecordDO::getRecordDate, range.start())
            .le(LedgerRecordDO::getRecordDate, range.end()));
        return LedgerSummaryCalculator.calculate(records, range);
    }

    private LedgerRecordDO getOwnedRecord(Long id, Long userId) {
        if (id == null) {
            throw new BusinessException("记录 ID 不能为空");
        }
        LedgerRecordDO record = ledgerRecordMapper.selectById(id);
        if (record == null || !record.getUserId().equals(userId)) {
            // 越权与不存在同一话术，避免探测他人记录
            throw new AppBizException(AppBizException.NOT_FOUND, "记账记录不存在");
        }
        return record;
    }

    private void validateCategory(LedgerTypeEnum type, String category) {
        if (!LedgerCategories.isValid(type, category)) {
            throw new BusinessException("该分类不属于所选收支类型");
        }
    }

    private static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BusinessException("月份格式应为 yyyy-MM");
        }
    }

    private static LedgerRecordResp toResp(LedgerRecordDO record) {
        LedgerRecordResp resp = new LedgerRecordResp();
        resp.setId(record.getId());
        resp.setType(record.getType());
        resp.setAmount(record.getAmount());
        resp.setCategory(record.getCategory());
        resp.setRecordDate(record.getRecordDate());
        resp.setRemark(record.getRemark());
        resp.setCreateTime(record.getCreateTime());
        return resp;
    }

    private int page(PageQuery pageQuery) {
        return pageQuery == null || pageQuery.getPage() == null ? 1 : pageQuery.getPage();
    }

    private int size(PageQuery pageQuery) {
        return pageQuery == null || pageQuery.getSize() == null ? 10 : pageQuery.getSize();
    }
}
