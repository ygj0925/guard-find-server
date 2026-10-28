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
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.ledger.service.LedgerGroupService;
import com.guardfind.server.tool.mapper.ToolTaskMapper;
import com.guardfind.server.tool.model.entity.ToolTaskDO;
import com.guardfind.server.tool.model.req.ToolTaskReq;
import com.guardfind.server.tool.model.resp.ToolTaskResp;
import com.guardfind.server.tool.service.ToolTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.starter.core.exception.BusinessException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务分派实现。权限口径:
 * 创建=组成员;负责人必须同组(防跨组派单);状态流转=创建者或负责人;
 * 编辑/删除=仅创建者;list mine=负责人是我(跨组),list all=组内(成员校验)。
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Service
@RequiredArgsConstructor
public class ToolTaskServiceImpl implements ToolTaskService {

    private final ToolTaskMapper taskMapper;
    private final LedgerGroupService groupService;
    private final AppUserMapper appUserMapper;

    @Override
    public Long create(ToolTaskReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        groupService.requireMembership(req.getGroupId());
        // 负责人必须同组:防跨组/防把任务派给陌生人
        groupService.requireUserInGroup(req.getGroupId(), req.getAssigneeUserId());
        ToolTaskDO task = new ToolTaskDO();
        applyReq(task, req);
        task.setCreatorUserId(userId);
        taskMapper.insert(task);
        return task.getId();
    }

    @Override
    public List<ToolTaskResp> list(String scope, Long groupId) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        List<ToolTaskDO> tasks;
        if ("all".equals(scope)) {
            if (groupId == null) {
                throw new BusinessException("组内视图须指定家庭组");
            }
            groupService.requireMembership(groupId);
            tasks = taskMapper.selectList(Wrappers.<ToolTaskDO>lambdaQuery()
                .eq(ToolTaskDO::getGroupId, groupId)
                .orderByAsc(ToolTaskDO::getStatus)
                .orderByAsc(ToolTaskDO::getDueDate)
                .orderByDesc(ToolTaskDO::getId));
        } else {
            // 默认 mine:指派给我的,跨我的全部组
            tasks = taskMapper.selectList(Wrappers.<ToolTaskDO>lambdaQuery()
                .eq(ToolTaskDO::getAssigneeUserId, userId)
                .orderByAsc(ToolTaskDO::getStatus)
                .orderByAsc(ToolTaskDO::getDueDate)
                .orderByDesc(ToolTaskDO::getId));
        }
        return assemble(tasks);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        if (status == null || status < 1 || status > 3) {
            throw new BusinessException("状态取值不合法");
        }
        ToolTaskDO task = getTask(id);
        groupService.requireMembership(task.getGroupId());
        if (!Objects.equals(task.getAssigneeUserId(), userId) && !Objects.equals(task.getCreatorUserId(), userId)) {
            throw new BusinessException("只有负责人或创建者可以更新任务状态");
        }
        task.setStatus(status);
        taskMapper.updateById(task);
    }

    @Override
    public void update(Long id, ToolTaskReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ToolTaskDO task = getTask(id);
        if (!Objects.equals(task.getCreatorUserId(), userId)) {
            throw new BusinessException("只有创建者可以编辑任务");
        }
        groupService.requireMembership(req.getGroupId());
        groupService.requireUserInGroup(req.getGroupId(), req.getAssigneeUserId());
        applyReq(task, req);
        taskMapper.updateById(task);
    }

    @Override
    public void delete(Long id) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ToolTaskDO task = getTask(id);
        if (!Objects.equals(task.getCreatorUserId(), userId)) {
            throw new BusinessException("只有创建者可以删除任务");
        }
        taskMapper.deleteById(task.getId());
    }

    private void applyReq(ToolTaskDO task, ToolTaskReq req) {
        if (StrUtil.isBlank(req.getTitle())) {
            throw new BusinessException("任务标题不能为空");
        }
        task.setGroupId(req.getGroupId());
        task.setAssigneeUserId(req.getAssigneeUserId());
        task.setTitle(req.getTitle().trim());
        task.setDetail(req.getDetail());
        task.setDueDate(req.getDueDate());
        task.setPriority(req.getPriority() == null ? 2 : Integer.parseInt(req.getPriority()));
    }

    private ToolTaskDO getTask(Long id) {
        if (id == null) {
            throw new BusinessException("任务 ID 不能为空");
        }
        ToolTaskDO task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        return task;
    }

    /** 批量补负责人昵称,避免 N+1 */
    private List<ToolTaskResp> assemble(List<ToolTaskDO> tasks) {
        List<Long> userIds = tasks.stream().map(ToolTaskDO::getAssigneeUserId).distinct().toList();
        Map<Long, AppUserDO> users = userIds.isEmpty()
            ? Map.of()
            : appUserMapper.selectByIds(userIds)
                .stream()
                .collect(Collectors.toMap(AppUserDO::getId, Function.identity()));
        return tasks.stream().map(task -> {
            ToolTaskResp resp = new ToolTaskResp();
            resp.setId(task.getId());
            resp.setGroupId(task.getGroupId());
            resp.setTitle(task.getTitle());
            resp.setDetail(task.getDetail());
            resp.setAssigneeUserId(task.getAssigneeUserId());
            resp.setCreatorUserId(task.getCreatorUserId());
            resp.setDueDate(task.getDueDate());
            resp.setPriority(task.getPriority());
            resp.setStatus(task.getStatus());
            resp.setCreateTime(task.getCreateTime());
            AppUserDO user = users.get(task.getAssigneeUserId());
            if (user != null) {
                resp.setAssigneeNickname(user.getNickname());
            }
            return resp;
        }).toList();
    }
}
