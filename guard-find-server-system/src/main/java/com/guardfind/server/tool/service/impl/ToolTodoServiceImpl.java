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
import com.guardfind.server.tool.mapper.ToolTodoMapper;
import com.guardfind.server.tool.model.entity.ToolTodoDO;
import com.guardfind.server.tool.model.req.ToolTodoReq;
import com.guardfind.server.tool.model.resp.ToolTodoResp;
import com.guardfind.server.tool.service.ToolTodoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.starter.core.exception.BusinessException;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * 待办实现。排序在 Java 侧做:未完成(截止日升序,null 最后)在前,
 * 已完成(更新时间倒序)在后;数据量小,不值得写 SQL 排序空值处理。
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Service
@RequiredArgsConstructor
public class ToolTodoServiceImpl implements ToolTodoService {

    private final ToolTodoMapper todoMapper;

    @Override
    public Long create(ToolTodoReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        String title = requireTitle(req);
        ToolTodoDO record = new ToolTodoDO();
        record.setUserId(userId);
        record.setTitle(title);
        record.setDone(Boolean.TRUE.equals(req.getDone()) ? 1 : 0);
        record.setDueDate(req.getDueDate());
        todoMapper.insert(record);
        return record.getId();
    }

    @Override
    public void update(Long id, ToolTodoReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ToolTodoDO record = getOwned(id, userId);
        record.setTitle(requireTitle(req));
        record.setDone(Boolean.TRUE.equals(req.getDone()) ? 1 : 0);
        record.setDueDate(req.getDueDate());
        todoMapper.updateById(record);
    }

    @Override
    public void toggle(Long id) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ToolTodoDO record = getOwned(id, userId);
        record.setDone(record.getDone() != null && record.getDone() == 1 ? 0 : 1);
        todoMapper.updateById(record);
    }

    @Override
    public void delete(Long id) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        todoMapper.deleteById(getOwned(id, userId).getId());
    }

    @Override
    public List<ToolTodoResp> list() {
        Long userId = AppUserContextHolder.getUserIdRequired();
        List<ToolTodoDO> records = todoMapper.selectList(Wrappers.<ToolTodoDO>lambdaQuery()
            .eq(ToolTodoDO::getUserId, userId));
        records.sort(Comparator.comparing((ToolTodoDO record) -> record.getDone() != null && record.getDone() == 1)
            .thenComparing(record -> record.getDueDate() == null ? LocalDate.MAX : record.getDueDate())
            .thenComparing(ToolTodoDO::getId, Comparator.reverseOrder()));
        return records.stream().map(ToolTodoServiceImpl::toResp).toList();
    }

    private String requireTitle(ToolTodoReq req) {
        String title = StrUtil.trim(req.getTitle());
        if (StrUtil.isBlank(title)) {
            throw new BusinessException("待办内容不能为空");
        }
        return title;
    }

    private ToolTodoDO getOwned(Long id, Long userId) {
        if (id == null) {
            throw new BusinessException("记录 ID 不能为空");
        }
        ToolTodoDO record = todoMapper.selectById(id);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new AppBizException(AppBizException.NOT_FOUND, "待办不存在");
        }
        return record;
    }

    private static ToolTodoResp toResp(ToolTodoDO record) {
        ToolTodoResp resp = new ToolTodoResp();
        resp.setId(record.getId());
        resp.setTitle(record.getTitle());
        resp.setDone(record.getDone() != null && record.getDone() == 1);
        resp.setDueDate(record.getDueDate());
        resp.setCreateTime(record.getCreateTime());
        return resp;
    }
}
