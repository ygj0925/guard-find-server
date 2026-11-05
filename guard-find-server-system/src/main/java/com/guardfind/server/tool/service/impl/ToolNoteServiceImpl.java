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
import com.guardfind.server.tool.mapper.ToolNoteMapper;
import com.guardfind.server.tool.model.entity.ToolNoteDO;
import com.guardfind.server.tool.model.req.ToolNoteReq;
import com.guardfind.server.tool.model.resp.ToolNoteResp;
import com.guardfind.server.tool.service.ToolNoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 灵感便笺实现。所有读写都带 user_id，修改使用 version 条件更新防止覆盖。
 *
 * @author guardfind
 * @since 2026/11/06
 */
@Service
@RequiredArgsConstructor
public class ToolNoteServiceImpl implements ToolNoteService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ToolNoteMapper noteMapper;

    @Override
    public Long create(ToolNoteReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ToolNoteDO record = new ToolNoteDO();
        record.setUserId(userId);
        record.setTitle(requireTitle(req));
        record.setContent(StrUtil.nullToEmpty(req.getContent()));
        record.setPinned(0);
        record.setArchived(Boolean.TRUE.equals(req.getArchived()) ? 1 : 0);
        record.setVersion(1);
        noteMapper.insert(record);
        return record.getId();
    }

    @Override
    public void update(Long id, ToolNoteReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        getOwned(id, userId);
        Integer version = req.getVersion();
        if (version == null) {
            throw new BusinessException("缺少版本号，请刷新后重试");
        }
        int updated = noteMapper.update(null, Wrappers.<ToolNoteDO>lambdaUpdate()
            .eq(ToolNoteDO::getId, id)
            .eq(ToolNoteDO::getUserId, userId)
            .eq(ToolNoteDO::getVersion, version)
            .set(ToolNoteDO::getTitle, requireTitle(req))
            .set(ToolNoteDO::getContent, StrUtil.nullToEmpty(req.getContent()))
            .set(ToolNoteDO::getArchived, Boolean.TRUE.equals(req.getArchived()) ? 1 : 0)
            .set(ToolNoteDO::getUpdateTime, LocalDateTime.now())
            .setSql("version = version + 1"));
        if (updated == 0) {
            throw new AppBizException(AppBizException.STATE_CONFLICT, "便笺已被修改，请刷新后重试");
        }
    }

    @Override
    public void togglePin(Long id) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        getOwned(id, userId);
        noteMapper.update(null, Wrappers.<ToolNoteDO>lambdaUpdate()
            .eq(ToolNoteDO::getId, id)
            .eq(ToolNoteDO::getUserId, userId)
            .setSql("pinned = 1 - pinned")
            .set(ToolNoteDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public void toggleArchive(Long id) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        getOwned(id, userId);
        noteMapper.update(null, Wrappers.<ToolNoteDO>lambdaUpdate()
            .eq(ToolNoteDO::getId, id)
            .eq(ToolNoteDO::getUserId, userId)
            .setSql("archived = 1 - archived")
            .set(ToolNoteDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public void delete(Long id) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        noteMapper.deleteById(getOwned(id, userId).getId());
    }

    @Override
    public PageResp<ToolNoteResp> page(int page, int size, String keyword, boolean archived) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        int pageNum = Math.max(1, page);
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
        String kw = StrUtil.trimToNull(keyword);
        LambdaQueryWrapper<ToolNoteDO> query = Wrappers.<ToolNoteDO>lambdaQuery()
            .eq(ToolNoteDO::getUserId, userId)
            .eq(ToolNoteDO::getArchived, archived ? 1 : 0);
        if (kw != null) {
            query.and(w -> w.like(ToolNoteDO::getTitle, kw).or().like(ToolNoteDO::getContent, kw));
        }
        query.last("ORDER BY pinned DESC, COALESCE(update_time, create_time) DESC, id DESC");
        Page<ToolNoteDO> result = noteMapper.selectPage(new Page<>(pageNum, pageSize), query);
        List<ToolNoteResp> list = result.getRecords().stream().map(ToolNoteServiceImpl::toResp).toList();
        PageResp<ToolNoteResp> resp = new PageResp<>();
        resp.setList(list);
        resp.setTotal(result.getTotal());
        return resp;
    }

    private String requireTitle(ToolNoteReq req) {
        String title = StrUtil.trim(req.getTitle());
        if (StrUtil.isBlank(title)) {
            throw new BusinessException("标题不能为空");
        }
        return title;
    }

    private ToolNoteDO getOwned(Long id, Long userId) {
        if (id == null) {
            throw new BusinessException("记录 ID 不能为空");
        }
        ToolNoteDO record = noteMapper.selectById(id);
        if (record == null || !userId.equals(record.getUserId())) {
            throw new AppBizException(AppBizException.NOT_FOUND, "便笺不存在");
        }
        return record;
    }

    static ToolNoteResp toResp(ToolNoteDO record) {
        ToolNoteResp resp = new ToolNoteResp();
        resp.setId(record.getId());
        resp.setTitle(record.getTitle());
        resp.setContent(record.getContent());
        resp.setPinned(record.getPinned() != null && record.getPinned() == 1);
        resp.setArchived(record.getArchived() != null && record.getArchived() == 1);
        resp.setVersion(record.getVersion());
        resp.setCreateTime(record.getCreateTime());
        resp.setUpdateTime(record.getUpdateTime());
        return resp;
    }
}
