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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.exception.AppBizException;
import com.guardfind.server.tool.mapper.ToolItemMapper;
import com.guardfind.server.tool.model.entity.ToolItemDO;
import com.guardfind.server.tool.model.req.ToolItemReq;
import com.guardfind.server.tool.model.resp.ToolItemResp;
import com.guardfind.server.tool.service.ToolItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 物品收纳实现。数量增减用当前值做条件更新，避免并发下减成负数或覆盖。
 *
 * @author guardfind
 * @since 2026/11/06
 */
@Service
@RequiredArgsConstructor
public class ToolItemServiceImpl implements ToolItemService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_QUANTITY = 999999;

    private final ToolItemMapper itemMapper;

    @Override
    public Long create(ToolItemReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        int quantity = req.getQuantity() == null ? 1 : req.getQuantity();
        requireValidQuantity(quantity);
        ToolItemDO record = new ToolItemDO();
        record.setUserId(userId);
        record.setName(requireName(req));
        record.setLocation(StrUtil.trimToEmpty(req.getLocation()));
        record.setQuantity(quantity);
        record.setNote(StrUtil.nullToEmpty(req.getNote()));
        record.setArchived(Boolean.TRUE.equals(req.getArchived()) ? 1 : 0);
        record.setVersion(1);
        itemMapper.insert(record);
        return record.getId();
    }

    @Override
    public void update(Long id, ToolItemReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        getOwned(id, userId);
        Integer version = req.getVersion();
        if (version == null) {
            throw new BusinessException("缺少版本号，请刷新后重试");
        }
        int quantity = req.getQuantity() == null ? 1 : req.getQuantity();
        requireValidQuantity(quantity);
        int updated = itemMapper.update(null, Wrappers.<ToolItemDO>lambdaUpdate()
            .eq(ToolItemDO::getId, id)
            .eq(ToolItemDO::getUserId, userId)
            .eq(ToolItemDO::getVersion, version)
            .set(ToolItemDO::getName, requireName(req))
            .set(ToolItemDO::getLocation, StrUtil.trimToEmpty(req.getLocation()))
            .set(ToolItemDO::getQuantity, quantity)
            .set(ToolItemDO::getNote, StrUtil.nullToEmpty(req.getNote()))
            .set(ToolItemDO::getArchived, Boolean.TRUE.equals(req.getArchived()) ? 1 : 0)
            .set(ToolItemDO::getUpdateTime, LocalDateTime.now())
            .setSql("version = version + 1"));
        if (updated == 0) {
            throw new AppBizException(AppBizException.STATE_CONFLICT, "物品已被修改，请刷新后重试");
        }
    }

    @Override
    public void changeQuantity(Long id, int delta) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ToolItemDO record = getOwned(id, userId);
        int current = record.getQuantity() == null ? 0 : record.getQuantity();
        int next = current + delta;
        if (next < 0) {
            throw new BusinessException("数量不能为负数");
        }
        requireValidQuantity(next);
        int updated = itemMapper.update(null, Wrappers.<ToolItemDO>lambdaUpdate()
            .eq(ToolItemDO::getId, id)
            .eq(ToolItemDO::getUserId, userId)
            .eq(ToolItemDO::getQuantity, current)
            .set(ToolItemDO::getQuantity, next)
            .set(ToolItemDO::getUpdateTime, LocalDateTime.now())
            .setSql("version = version + 1"));
        if (updated == 0) {
            throw new AppBizException(AppBizException.STATE_CONFLICT, "数量已变化，请刷新后重试");
        }
    }

    @Override
    public void toggleArchive(Long id) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        getOwned(id, userId);
        itemMapper.update(null, Wrappers.<ToolItemDO>lambdaUpdate()
            .eq(ToolItemDO::getId, id)
            .eq(ToolItemDO::getUserId, userId)
            .setSql("archived = 1 - archived")
            .set(ToolItemDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public void delete(Long id) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        itemMapper.deleteById(getOwned(id, userId).getId());
    }

    @Override
    public PageResp<ToolItemResp> page(int page, int size, String keyword, String location, boolean archived) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        int pageNum = Math.max(1, page);
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
        String kw = StrUtil.trimToNull(keyword);
        String loc = StrUtil.trimToNull(location);
        LambdaQueryWrapper<ToolItemDO> query = Wrappers.<ToolItemDO>lambdaQuery()
            .eq(ToolItemDO::getUserId, userId)
            .eq(ToolItemDO::getArchived, archived ? 1 : 0)
            .like(loc != null, ToolItemDO::getLocation, loc);
        if (kw != null) {
            query.and(w -> w.like(ToolItemDO::getName, kw)
                .or()
                .like(ToolItemDO::getLocation, kw)
                .or()
                .like(ToolItemDO::getNote, kw));
        }
        query.last("ORDER BY location ASC, name ASC, id ASC");
        Page<ToolItemDO> result = itemMapper.selectPage(new Page<>(pageNum, pageSize), query);
        List<ToolItemResp> list = result.getRecords().stream().map(ToolItemServiceImpl::toResp).toList();
        PageResp<ToolItemResp> resp = new PageResp<>();
        resp.setList(list);
        resp.setTotal(result.getTotal());
        return resp;
    }

    private String requireName(ToolItemReq req) {
        String name = StrUtil.trim(req.getName());
        if (StrUtil.isBlank(name)) {
            throw new BusinessException("物品名称不能为空");
        }
        return name;
    }

    private static void requireValidQuantity(int quantity) {
        if (quantity < 0) {
            throw new BusinessException("数量不能为负数");
        }
        if (quantity > MAX_QUANTITY) {
            throw new BusinessException("数量不能超过 " + MAX_QUANTITY);
        }
    }

    private ToolItemDO getOwned(Long id, Long userId) {
        if (id == null) {
            throw new BusinessException("记录 ID 不能为空");
        }
        ToolItemDO record = itemMapper.selectById(id);
        if (record == null || !userId.equals(record.getUserId())) {
            throw new AppBizException(AppBizException.NOT_FOUND, "物品不存在");
        }
        return record;
    }

    static ToolItemResp toResp(ToolItemDO record) {
        ToolItemResp resp = new ToolItemResp();
        resp.setId(record.getId());
        resp.setName(record.getName());
        resp.setLocation(record.getLocation());
        resp.setQuantity(record.getQuantity());
        resp.setNote(record.getNote());
        resp.setArchived(record.getArchived() != null && record.getArchived() == 1);
        resp.setVersion(record.getVersion());
        resp.setCreateTime(record.getCreateTime());
        resp.setUpdateTime(record.getUpdateTime());
        return resp;
    }
}
