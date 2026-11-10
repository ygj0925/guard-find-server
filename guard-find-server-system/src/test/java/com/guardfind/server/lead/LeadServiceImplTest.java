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

package com.guardfind.server.lead;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.exception.AppBizException;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.findcase.mapper.FindCaseMapper;
import com.guardfind.server.findcase.model.entity.FindCaseDO;
import com.guardfind.server.lead.constant.LeadConstants;
import com.guardfind.server.lead.enums.LeadReplyFromEnum;
import com.guardfind.server.lead.enums.LeadStatusEnum;
import com.guardfind.server.lead.mapper.LeadMapper;
import com.guardfind.server.lead.mapper.LeadReplyMapper;
import com.guardfind.server.lead.model.entity.LeadDO;
import com.guardfind.server.lead.model.entity.LeadReplyDO;
import com.guardfind.server.lead.model.req.LeadReplyReq;
import com.guardfind.server.lead.model.req.LeadStatusReq;
import com.guardfind.server.lead.model.resp.LeadPageResp;
import com.guardfind.server.lead.model.resp.LeadReplyResp;
import com.guardfind.server.lead.service.impl.LeadServiceImpl;
import com.guardfind.server.lead.support.LeadRateLimiter;
import com.guardfind.server.lead.support.LeadViewAssembler;
import com.guardfind.server.moderation.enums.SensitiveCategoryEnum;
import com.guardfind.server.moderation.model.ContentCheckResult;
import com.guardfind.server.moderation.service.SensitiveWordMatcher;
import com.guardfind.server.notification.enums.NotificationEventEnum;
import com.guardfind.server.notification.service.AppMessageService;
import com.guardfind.server.support.ContainerlessTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import top.continew.starter.core.exception.BadRequestException;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.query.PageQuery;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link LeadServiceImpl#pageByCase} 单测:无权查看线索必须返回可枚举的业务码,客户端据此展示无权限态。
 *
 * @author guardfind
 * @since 2026/11/11
 */
class LeadServiceImplTest extends ContainerlessTestBase {

    private LeadMapper leadMapper;
    private LeadReplyMapper leadReplyMapper;
    private FindCaseMapper findCaseMapper;
    private AppMessageService appMessageService;
    private SensitiveWordMatcher sensitiveWordMatcher;
    private LeadViewAssembler leadViewAssembler;
    private LeadServiceImpl service;

    @BeforeEach
    void setUp() {
        leadMapper = mock(LeadMapper.class);
        leadReplyMapper = mock(LeadReplyMapper.class);
        findCaseMapper = mock(FindCaseMapper.class);
        appMessageService = mock(AppMessageService.class);
        sensitiveWordMatcher = mock(SensitiveWordMatcher.class);
        leadViewAssembler = mock(LeadViewAssembler.class);
        service = new LeadServiceImpl(leadMapper, leadReplyMapper, findCaseMapper, mock(AppUserMapper.class), appMessageService, sensitiveWordMatcher, mock(LeadRateLimiter.class), leadViewAssembler);
    }

    private static FindCaseDO caseOwnedBy(Long ownerId) {
        FindCaseDO caseDO = new FindCaseDO();
        caseDO.setId(100L);
        caseDO.setOwnerId(ownerId);
        return caseDO;
    }

    @Test
    @DisplayName("非发布人且非志愿者查看线索:抛 4003,且不查询线索")
    void strangerIsRejectedWithRuleCode() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(9L);
            when(AppUserContextHolder.isVolunteer()).thenReturn(false);
            when(findCaseMapper.selectById(100L)).thenReturn(caseOwnedBy(7L));

            AppBizException ex = assertThrows(AppBizException.class, () -> service
                .pageByCase(100L, new PageQuery(1, 20)));

            assertEquals(AppBizException.RULE_REJECTED, ex.getCode());
            verify(leadMapper, never()).selectPage(any(), any());
        }
    }

    @Test
    @DisplayName("档案发布人可查看线索,并按明文视角组装")
    void ownerCanViewLeads() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(findCaseMapper.selectById(100L)).thenReturn(caseOwnedBy(7L));
            when(leadMapper.selectPage(any(), any())).thenReturn(new Page<LeadDO>(1, 20));
            when(leadViewAssembler.toRespList(anyList(), anyBoolean())).thenReturn(List.of());

            LeadPageResp resp = service.pageByCase(100L, new PageQuery(1, 20));

            assertNotNull(resp);
            verify(leadViewAssembler).toRespList(anyList(), org.mockito.ArgumentMatchers.eq(true));
        }
    }

    @Test
    @DisplayName("已认证志愿者可查看线索,但联系方式按掩码视角组装")
    void volunteerCanViewMaskedLeads() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(9L);
            when(AppUserContextHolder.isVolunteer()).thenReturn(true);
            when(findCaseMapper.selectById(100L)).thenReturn(caseOwnedBy(7L));
            when(leadMapper.selectPage(any(), any())).thenReturn(new Page<LeadDO>(1, 20));
            when(leadViewAssembler.toRespList(anyList(), anyBoolean())).thenReturn(List.of());

            service.pageByCase(100L, new PageQuery(1, 20));

            verify(leadViewAssembler).toRespList(anyList(), org.mockito.ArgumentMatchers.eq(false));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("陌生人和已认证非发布人志愿者不能处置线索,权限校验先于请求校验")
    void nonOwnerCannotUpdateStatusBeforeRequestValidation(boolean volunteer) {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(9L);
            when(AppUserContextHolder.isVolunteer()).thenReturn(volunteer);
            stubLead(11L);

            assertThrowsExactly(BusinessException.class, () -> service.updateStatus(200L, null));
            assertThrowsExactly(BusinessException.class, () -> service.updateStatus(200L, statusReq("UNKNOWN", null)));
            assertThrowsExactly(BusinessException.class, () -> service
                .updateStatus(200L, statusReq("ACCEPTED", "已核实")));

            assertNoWritesOrMessages();
            verifyNoInteractions(sensitiveWordMatcher);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("陌生人和已认证非发布人志愿者不能回复,权限校验先于请求校验和风控")
    void nonOwnerCannotReplyBeforeValidationOrModeration(boolean volunteer) {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(9L);
            when(AppUserContextHolder.isVolunteer()).thenReturn(volunteer);
            stubLead(11L);

            assertThrowsExactly(BusinessException.class, () -> service.reply(200L, null));
            assertThrowsExactly(BusinessException.class, () -> service.reply(200L, replyReq("   ")));
            assertThrowsExactly(BusinessException.class, () -> service.reply(200L, replyReq("请提供照片")));

            assertNoWritesOrMessages();
            verifyNoInteractions(sensitiveWordMatcher);
        }
    }

    @ParameterizedTest
    @EnumSource(value = LeadStatusEnum.class, names = {"ACCEPTED", "REJECTED", "CONTACTED"})
    @DisplayName("发布人可采纳、排除或联系:说明去空白、显式审计,仅通知提交人")
    void ownerCanUpdateActionableStatusWithAuditAndNotifySubmitter(LeadStatusEnum status) {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            stubLead(11L);
            LocalDateTime before = LocalDateTime.now();

            service.updateStatus(200L, statusReq(status.name(), "  已核实  "));

            LocalDateTime after = LocalDateTime.now();
            ArgumentCaptor<LeadDO> captor = ArgumentCaptor.forClass(LeadDO.class);
            verify(leadMapper).updateById(captor.capture());
            LeadDO update = captor.getValue();
            assertEquals(200L, update.getId());
            assertEquals(status, update.getStatus());
            assertEquals("已核实", update.getHandleRemark());
            assertEquals(7L, update.getUpdateUser());
            assertTimeWithin(update.getUpdateTime(), before, after);
            verify(appMessageService)
                .send(eq(11L), eq(NotificationEventEnum.LEAD_REPLIED), eq(LeadConstants.MESSAGE_BIZ_TYPE), eq(200L), anyString(), anyString());
            verifyNoMoreInteractions(appMessageService);
            verify(leadMapper, never()).insert(any(LeadDO.class));
            verifyNoInteractions(leadReplyMapper, sensitiveWordMatcher);
        }
    }

    @Test
    @DisplayName("处置无提交人的线索仍更新,空白说明置空且不发送站内信")
    void ownerStatusUpdateWithoutSubmitterSkipsMessageAndClearsBlankRemark() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            stubLead(null);

            service.updateStatus(200L, statusReq("CONTACTED", "   "));

            ArgumentCaptor<LeadDO> captor = ArgumentCaptor.forClass(LeadDO.class);
            verify(leadMapper).updateById(captor.capture());
            assertEquals(LeadStatusEnum.CONTACTED, captor.getValue().getStatus());
            assertNull(captor.getValue().getHandleRemark());
            verifyNoInteractions(appMessageService, leadReplyMapper, sensitiveWordMatcher);
        }
    }

    @Test
    @DisplayName("发布人回复去空白后风控,写入 OWNER 和审计字段,返回生成 ID 并仅通知提交人")
    void ownerReplyIsTrimmedModeratedAndInsertedWithAudit() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            stubLead(11L);
            when(sensitiveWordMatcher.match("请提供照片")).thenReturn(ContentCheckResult.pass());
            stubReplyInsert();
            LocalDateTime before = LocalDateTime.now();

            LeadReplyResp resp = service.reply(200L, replyReq("  请提供照片  "));

            LocalDateTime after = LocalDateTime.now();
            verify(sensitiveWordMatcher).match("请提供照片");
            verifyNoMoreInteractions(sensitiveWordMatcher);
            ArgumentCaptor<LeadReplyDO> captor = ArgumentCaptor.forClass(LeadReplyDO.class);
            verify(leadReplyMapper).insert(captor.capture());
            LeadReplyDO inserted = captor.getValue();
            assertEquals(200L, inserted.getLeadId());
            assertEquals(LeadReplyFromEnum.OWNER, inserted.getFromSide());
            assertEquals(7L, inserted.getFromUserId());
            assertEquals("请提供照片", inserted.getContent());
            assertEquals(7L, inserted.getCreateUser());
            assertEquals(7L, inserted.getUpdateUser());
            assertTimeWithin(inserted.getCreateTime(), before, after);
            assertEquals(inserted.getCreateTime(), inserted.getUpdateTime());
            assertEquals(300L, resp.getId());
            assertEquals(inserted.getId(), resp.getId());
            assertEquals("请提供照片", resp.getContent());
            assertEquals(LeadReplyFromEnum.OWNER, resp.getFrom());
            assertEquals(inserted.getCreateTime(), resp.getTime());
            verify(appMessageService)
                .send(eq(11L), eq(NotificationEventEnum.LEAD_REPLIED), eq(LeadConstants.MESSAGE_BIZ_TYPE), eq(200L), eq("家属回复了您的线索"), anyString());
            verifyNoMoreInteractions(appMessageService, leadReplyMapper);
            verify(leadMapper, never()).updateById(any(LeadDO.class));
            verify(leadMapper, never()).insert(any(LeadDO.class));
        }
    }

    @Test
    @DisplayName("回复无提交人的线索仍风控并插入,但不发送站内信")
    void ownerReplyWithoutSubmitterSkipsMessage() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            stubLead(null);
            when(sensitiveWordMatcher.match("请提供照片")).thenReturn(ContentCheckResult.pass());
            stubReplyInsert();

            LeadReplyResp resp = service.reply(200L, replyReq("  请提供照片  "));

            assertEquals(300L, resp.getId());
            verify(sensitiveWordMatcher).match("请提供照片");
            verify(leadReplyMapper).insert(any(LeadReplyDO.class));
            verifyNoInteractions(appMessageService);
            verify(leadMapper, never()).updateById(any(LeadDO.class));
            verify(leadMapper, never()).insert(any(LeadDO.class));
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "UNKNOWN"})
    @DisplayName("发布人传入空白或未知状态:抛 BadRequestException,不写入或通知")
    void ownerInvalidStatusIsRejectedWithoutWrites(String status) {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            stubLead(11L);

            assertThrowsExactly(BadRequestException.class, () -> service.updateStatus(200L, statusReq(status, "已核实")));

            assertNoWritesOrMessages();
            verifyNoInteractions(sensitiveWordMatcher);
        }
    }

    @Test
    @DisplayName("发布人不能改回 PENDING:抛 BusinessException,不写入或通知")
    void ownerCannotResetStatusToPending() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            stubLead(11L);

            assertThrowsExactly(BusinessException.class, () -> service.updateStatus(200L, statusReq("PENDING", null)));

            assertNoWritesOrMessages();
            verifyNoInteractions(sensitiveWordMatcher);
        }
    }

    @Test
    @DisplayName("发布人缺少处置或回复请求:抛 BadRequestException,不写入、风控或通知")
    void ownerNullRequestsAreRejectedBeforeSideEffects() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            stubLead(11L);

            assertThrowsExactly(BadRequestException.class, () -> service.updateStatus(200L, null));
            assertThrowsExactly(BadRequestException.class, () -> service.reply(200L, null));

            assertNoWritesOrMessages();
            verifyNoInteractions(sensitiveWordMatcher);
        }
    }

    @Test
    @DisplayName("发布人回复内容为空或超长:抛 BadRequestException,不风控、写入或通知")
    void ownerInvalidReplyContentIsRejectedBeforeModeration() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            stubLead(11L);

            assertThrowsExactly(BadRequestException.class, () -> service.reply(200L, replyReq(null)));
            for (String content : List.of("", "   ", "字".repeat(LeadConstants.MAX_REPLY_LENGTH + 1))) {
                assertThrowsExactly(BadRequestException.class, () -> service.reply(200L, replyReq(content)));
            }

            assertNoWritesOrMessages();
            verifyNoInteractions(sensitiveWordMatcher);
        }
    }

    @ParameterizedTest
    @EnumSource(value = SensitiveCategoryEnum.class, names = {"ABUSE", "ILLEGAL"})
    @DisplayName("发布人回复命中拒绝类敏感词:抛 BadRequestException,不写入或通知")
    void ownerSensitiveReplyIsRejectedWithoutWrites(SensitiveCategoryEnum category) {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            stubLead(11L);
            when(sensitiveWordMatcher.match("不合规内容")).thenReturn(ContentCheckResult.of(List.of("不合规内容"), List
                .of(category)));

            assertThrowsExactly(BadRequestException.class, () -> service.reply(200L, replyReq("  不合规内容  ")));

            verify(sensitiveWordMatcher).match("不合规内容");
            verifyNoMoreInteractions(sensitiveWordMatcher);
            assertNoWritesOrMessages();
        }
    }

    private void stubLead(Long submitterId) {
        LeadDO lead = new LeadDO();
        lead.setId(200L);
        lead.setCaseId(100L);
        lead.setSubmitterId(submitterId);
        lead.setStatus(LeadStatusEnum.PENDING);
        when(leadMapper.selectById(200L)).thenReturn(lead);
        when(findCaseMapper.selectById(100L)).thenReturn(caseOwnedBy(7L));
    }

    private void stubReplyInsert() {
        when(leadReplyMapper.insert(any(LeadReplyDO.class))).thenAnswer(invocation -> {
            LeadReplyDO reply = invocation.getArgument(0);
            reply.setId(300L);
            return 1;
        });
    }

    private static LeadStatusReq statusReq(String status, String remark) {
        LeadStatusReq req = new LeadStatusReq();
        req.setStatus(status);
        req.setRemark(remark);
        return req;
    }

    private static LeadReplyReq replyReq(String content) {
        LeadReplyReq req = new LeadReplyReq();
        req.setContent(content);
        return req;
    }

    private void assertNoWritesOrMessages() {
        verify(leadMapper, never()).updateById(any(LeadDO.class));
        verify(leadMapper, never()).insert(any(LeadDO.class));
        verifyNoInteractions(leadReplyMapper, appMessageService);
    }

    private static void assertTimeWithin(LocalDateTime actual, LocalDateTime before, LocalDateTime after) {
        assertNotNull(actual);
        assertFalse(actual.isBefore(before));
        assertFalse(actual.isAfter(after));
    }
}
