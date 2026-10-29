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

package com.guardfind.server.tool;

import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.app.exception.AppBizException;
import com.guardfind.server.notification.service.AppMessageService;
import com.guardfind.server.tool.mapper.ToolReportCommentMapper;
import com.guardfind.server.tool.mapper.ToolReportEntryMapper;
import com.guardfind.server.tool.mapper.ToolReportMapper;
import com.guardfind.server.tool.mapper.ToolReportMemberMapper;
import com.guardfind.server.tool.model.entity.ToolReportDO;
import com.guardfind.server.tool.model.entity.ToolReportEntryDO;
import com.guardfind.server.tool.model.entity.ToolReportMemberDO;
import com.guardfind.server.tool.model.req.ToolReportEntryReq;
import com.guardfind.server.tool.service.impl.ToolReportServiceImpl;
import com.guardfind.server.support.ContainerlessTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 月度工作汇报单测:权限矩阵(非成员/填报人本人/草稿隐私/角色限制)与迟交标记。
 */
class ToolReportServiceImplTest extends ContainerlessTestBase {

    private ToolReportMapper reportMapper;
    private ToolReportMemberMapper memberMapper;
    private ToolReportEntryMapper entryMapper;
    private ToolReportCommentMapper commentMapper;
    private AppUserMapper appUserMapper;
    private AppMessageService messageService;
    private ToolReportServiceImpl service;

    @BeforeEach
    void setUp() {
        reportMapper = mock(ToolReportMapper.class);
        memberMapper = mock(ToolReportMemberMapper.class);
        entryMapper = mock(ToolReportEntryMapper.class);
        commentMapper = mock(ToolReportCommentMapper.class);
        appUserMapper = mock(AppUserMapper.class);
        messageService = mock(AppMessageService.class);
        service = new ToolReportServiceImpl(reportMapper, memberMapper, entryMapper, commentMapper, appUserMapper, messageService);
    }

    private static ToolReportDO activity(Long id, Long ownerId) {
        ToolReportDO activity = new ToolReportDO();
        activity.setId(id);
        activity.setTitle("10 月月度工作汇报");
        activity.setPeriod("2026-10");
        activity.setUserId(ownerId);
        activity.setDueDate(null);
        return activity;
    }

    private static ToolReportMemberDO membership(Long activityId, Long userId, int role) {
        ToolReportMemberDO member = new ToolReportMemberDO();
        member.setActivityId(activityId);
        member.setUserId(userId);
        member.setRole(role);
        return member;
    }

    private static ToolReportEntryReq entryReq(String content, boolean submit) {
        ToolReportEntryReq req = new ToolReportEntryReq();
        req.setContent(content);
        req.setSubmit(submit);
        return req;
    }

    @Test
    @DisplayName("非成员读取月报一律 NOT_FOUND,不泄露活动存在性")
    void entryRejectsNonMember() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(reportMapper.selectById(5L)).thenReturn(activity(5L, 8L));
            when(memberMapper.selectOne(any())).thenReturn(null);
            AppBizException ex = assertThrows(AppBizException.class, () -> service.entry(5L, 7L));
            assertEquals(AppBizException.NOT_FOUND, ex.getCode());
        }
    }

    @Test
    @DisplayName("接收人(VIEWER)不能填报")
    void saveEntryRejectsViewer() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(reportMapper.selectById(5L)).thenReturn(activity(5L, 8L));
            when(memberMapper.selectOne(any())).thenReturn(membership(5L, 7L, 3));
            AppBizException ex = assertThrows(AppBizException.class, () -> service.saveEntry(5L, entryReq("内容", true)));
            assertEquals(AppBizException.RULE_REJECTED, ex.getCode());
            verify(entryMapper, never()).insert(any(ToolReportEntryDO.class));
        }
    }

    @Test
    @DisplayName("填报人保存:提交置状态 2 并记 submittedAt")
    void saveEntrySubmits() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(reportMapper.selectById(5L)).thenReturn(activity(5L, 8L));
            when(memberMapper.selectOne(any())).thenReturn(membership(5L, 7L, 2));
            when(entryMapper.selectOne(any())).thenReturn(null);
            when(entryMapper.insert(any(ToolReportEntryDO.class))).thenAnswer(invocation -> {
                ToolReportEntryDO saved = invocation.getArgument(0);
                saved.setId(66L);
                return 1;
            });

            service.saveEntry(5L, entryReq("本月完成 A/B/C", true));

            ArgumentCaptor<ToolReportEntryDO> captor = ArgumentCaptor.forClass(ToolReportEntryDO.class);
            verify(entryMapper).insert(captor.capture());
            assertEquals(2, captor.getValue().getStatus());
            assertEquals(7L, captor.getValue().getUserId());
            assertEquals("本月完成 A/B/C", captor.getValue().getContent());
        }
    }

    @Test
    @DisplayName("草稿隐私:他人只能读到已提交,草稿返回空")
    void draftHiddenFromOthers() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(9L);
            when(reportMapper.selectById(5L)).thenReturn(activity(5L, 8L));
            when(memberMapper.selectOne(any())).thenReturn(membership(5L, 9L, 3));
            ToolReportEntryDO draft = new ToolReportEntryDO();
            draft.setActivityId(5L);
            draft.setUserId(7L);
            draft.setContent("草稿内容");
            draft.setStatus(1);
            when(entryMapper.selectOne(any())).thenReturn(draft);

            var resp = service.entry(5L, 7L);
            assertEquals(0, resp.getStatus());
            assertEquals(null, resp.getContent());
        }
    }

    @Test
    @DisplayName("迟交:提交晚于截止时间标记 late")
    void lateSubmissionMarked() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            ToolReportDO activity = activity(5L, 8L);
            activity.setDueDate(LocalDateTime.now().minusDays(1));
            when(reportMapper.selectById(5L)).thenReturn(activity);
            when(memberMapper.selectOne(any())).thenReturn(membership(5L, 7L, 2));
            when(entryMapper.selectOne(any())).thenReturn(null);
            when(entryMapper.insert(any(ToolReportEntryDO.class))).thenReturn(1);

            service.saveEntry(5L, entryReq("补交", true));

            ArgumentCaptor<ToolReportEntryDO> captor = ArgumentCaptor.forClass(ToolReportEntryDO.class);
            verify(entryMapper).insert(captor.capture());
            assertEquals(2, captor.getValue().getStatus());
        }
    }

    @Test
    @DisplayName("仅发起人可删除活动;非发起人 RULE_REJECTED")
    void deleteOwnerOnly() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(reportMapper.selectById(5L)).thenReturn(activity(5L, 8L));
            when(memberMapper.selectOne(any())).thenReturn(membership(5L, 7L, 2));
            assertThrows(AppBizException.class, () -> service.delete(5L));
            verify(reportMapper, never()).deleteById(any(Long.class));

            when(memberMapper.selectOne(any())).thenReturn(membership(5L, 7L, 1));
            when(reportMapper.selectById(5L)).thenReturn(activity(5L, 7L));
            service.delete(5L);
            verify(reportMapper).deleteById(5L);
        }
    }
}
