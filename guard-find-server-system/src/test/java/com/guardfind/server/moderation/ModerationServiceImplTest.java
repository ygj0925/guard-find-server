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

package com.guardfind.server.moderation;

import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.common.context.UserContextHolder;
import com.guardfind.server.findcase.enums.CaseStatusEnum;
import com.guardfind.server.findcase.mapper.CaseStatusLogMapper;
import com.guardfind.server.findcase.mapper.FavoriteMapper;
import com.guardfind.server.findcase.mapper.FindCaseMapper;
import com.guardfind.server.findcase.model.entity.CaseStatusLogDO;
import com.guardfind.server.findcase.model.entity.FindCaseDO;
import com.guardfind.server.findcase.service.impl.CaseStatusServiceImpl;
import com.guardfind.server.lead.mapper.LeadMapper;
import com.guardfind.server.moderation.enums.ModerationBizTypeEnum;
import com.guardfind.server.moderation.mapper.ReportMapper;
import com.guardfind.server.moderation.mapper.UserPunishmentMapper;
import com.guardfind.server.moderation.model.req.ModerationApproveReq;
import com.guardfind.server.moderation.service.impl.ModerationServiceImpl;
import com.guardfind.server.notification.enums.NotificationEventEnum;
import com.guardfind.server.notification.service.AppMessageService;
import com.guardfind.server.support.ContainerlessTestBase;
import com.guardfind.server.system.service.DictItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import top.continew.starter.core.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ModerationServiceImplTest extends ContainerlessTestBase {

    private final FindCaseMapper caseMapper = mock(FindCaseMapper.class);
    private final CaseStatusLogMapper logs = mock(CaseStatusLogMapper.class);
    private final AppMessageService messages = mock(AppMessageService.class);
    private ModerationServiceImpl service;

    @BeforeEach
    void setUp() {
        var statuses = new CaseStatusServiceImpl(caseMapper, logs, mock(FavoriteMapper.class), messages);
        service = new ModerationServiceImpl(caseMapper, mock(LeadMapper.class), mock(ReportMapper.class), mock(UserPunishmentMapper.class), mock(AppUserMapper.class), statuses, messages, mock(DictItemService.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "  已核实  "})
    void approvalUsesRealStatusServiceAndNotifiesAppOwner(String remark) {
        FindCaseDO item = pendingCase();
        when(caseMapper.selectById(100L)).thenReturn(item);
        var req = new ModerationApproveReq();
        req.setRemark(remark);
        try (var context = mockStatic(UserContextHolder.class)) {
            context.when(UserContextHolder::getUserId).thenReturn(90L);
            service.approve(ModerationBizTypeEnum.CASE, 100L, req);
        }
        String reason = remark == null || remark.isBlank() ? "内容审核通过" : remark.trim();
        assertThat(item.getStatus()).isEqualTo(CaseStatusEnum.PUBLISHED);
        var updates = ArgumentCaptor.forClass(FindCaseDO.class);
        verify(caseMapper, times(2)).updateById(updates.capture());
        FindCaseDO state = updates.getAllValues().get(0);
        assertThat(state.getId()).isEqualTo(100L);
        assertThat(state.getStatus()).isEqualTo(CaseStatusEnum.PUBLISHED);
        assertThat(state.getUpdateUser()).isEqualTo(90L);
        assertThat(state.getPublishTime()).isNotNull().isEqualTo(state.getLastConfirmTime());
        assertThat(state.getUpdateTime()).isEqualTo(state.getPublishTime());
        FindCaseDO audit = updates.getAllValues().get(1);
        assertThat(audit.getStatus()).isNull();
        assertThat(audit.getId()).isEqualTo(100L);
        assertThat(audit.getAuditRemark()).isEqualTo(reason);
        assertThat(audit.getUpdateUser()).isEqualTo(90L);
        assertThat(audit.getUpdateTime()).isNotNull();
        var log = ArgumentCaptor.forClass(CaseStatusLogDO.class);
        verify(logs).insert(log.capture());
        assertThat(log.getValue().getCaseId()).isEqualTo(100L);
        assertThat(log.getValue().getFromStatus()).isEqualTo(CaseStatusEnum.PENDING_REVIEW);
        assertThat(log.getValue().getToStatus()).isEqualTo(CaseStatusEnum.PUBLISHED);
        assertThat(log.getValue().getReasonCode()).isEqualTo("APPROVED");
        assertThat(log.getValue().getReason()).isEqualTo(reason);
        assertThat(log.getValue().getOperatorId()).isEqualTo(90L);
        assertThat(log.getValue().getOperatorType()).isEqualTo("SYS");
        assertThat(log.getValue().getCreateTime()).isEqualTo(state.getPublishTime());
        verify(messages)
            .send(eq(7L), eq(NotificationEventEnum.CASE_APPROVED), eq("FIND_CASE"), eq(100L), anyString(), anyString());
        verifyNoMoreInteractions(messages);
    }

    @ParameterizedTest
    @EnumSource(value = CaseStatusEnum.class, names = {"DRAFT", "REJECTED", "WITHDRAWN", "BLOCKED", "ERASED"})
    void approvalCannotBypassIllegalStatusTransitions(CaseStatusEnum status) {
        FindCaseDO item = pendingCase();
        item.setStatus(status);
        when(caseMapper.selectById(100L)).thenReturn(item);
        try (var context = mockStatic(UserContextHolder.class)) {
            context.when(UserContextHolder::getUserId).thenReturn(90L);
            assertThatThrownBy(() -> service.approve(ModerationBizTypeEnum.CASE, 100L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态冲突");
        }
        assertThat(item.getStatus()).isEqualTo(status);
        verify(caseMapper, never()).updateById(any(FindCaseDO.class));
        verifyNoInteractions(logs, messages);
    }

    @Test
    void alreadyPublishedApprovalDoesNotWriteOrNotifyAgain() {
        FindCaseDO item = pendingCase();
        item.setStatus(CaseStatusEnum.PUBLISHED);
        when(caseMapper.selectById(100L)).thenReturn(item);
        try (var context = mockStatic(UserContextHolder.class)) {
            context.when(UserContextHolder::getUserId).thenReturn(90L);
            assertThatThrownBy(() -> service.approve(ModerationBizTypeEnum.CASE, 100L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该档案已发布，无需重复审核");
        }
        verify(caseMapper, never()).updateById(any(FindCaseDO.class));
        verifyNoInteractions(logs, messages);
    }

    @Test
    void missingOperatorIsRejectedBeforeLoadingCase() {
        try (var context = mockStatic(UserContextHolder.class)) {
            context.when(UserContextHolder::getUserId).thenReturn(null);
            assertThatThrownBy(() -> service.approve(ModerationBizTypeEnum.CASE, 100L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请先登录后台账号");
        }
        verifyNoInteractions(caseMapper, logs, messages);
    }

    @Test
    void missingCaseDoesNotWriteOrNotify() {
        try (var context = mockStatic(UserContextHolder.class)) {
            context.when(UserContextHolder::getUserId).thenReturn(90L);
            assertThatThrownBy(() -> service.approve(ModerationBizTypeEnum.CASE, 100L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("档案不存在");
        }
        verify(caseMapper, never()).updateById(any(FindCaseDO.class));
        verifyNoInteractions(logs, messages);
    }

    @Test
    void missingAppOwnerNeverUsesAdminAsNotificationReceiver() {
        FindCaseDO item = pendingCase();
        item.setOwnerId(null);
        when(caseMapper.selectById(100L)).thenReturn(item);
        try (var context = mockStatic(UserContextHolder.class)) {
            context.when(UserContextHolder::getUserId).thenReturn(90L);
            service.approve(ModerationBizTypeEnum.CASE, 100L, null);
        }
        assertThat(item.getStatus()).isEqualTo(CaseStatusEnum.PUBLISHED);
        verify(logs).insert(any(CaseStatusLogDO.class));
        verifyNoInteractions(messages);
    }

    @Test
    void collectedCandidateMustUseCollectApprovalEndpoint() {
        assertThatThrownBy(() -> service.approve(ModerationBizTypeEnum.COLLECT_CANDIDATE, 100L, null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("采集审批接口");
        verifyNoInteractions(caseMapper, logs, messages);
    }

    private static FindCaseDO pendingCase() {
        var item = new FindCaseDO();
        item.setId(100L);
        item.setOwnerId(7L);
        item.setName("测试档案");
        item.setStatus(CaseStatusEnum.PENDING_REVIEW);
        return item;
    }
}
