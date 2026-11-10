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
import com.guardfind.server.lead.mapper.LeadMapper;
import com.guardfind.server.lead.mapper.LeadReplyMapper;
import com.guardfind.server.lead.model.entity.LeadDO;
import com.guardfind.server.lead.model.resp.LeadPageResp;
import com.guardfind.server.lead.service.impl.LeadServiceImpl;
import com.guardfind.server.lead.support.LeadRateLimiter;
import com.guardfind.server.lead.support.LeadViewAssembler;
import com.guardfind.server.moderation.service.SensitiveWordMatcher;
import com.guardfind.server.notification.service.AppMessageService;
import com.guardfind.server.support.ContainerlessTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.continew.starter.extension.crud.model.query.PageQuery;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LeadServiceImpl#pageByCase} 单测:无权查看线索必须返回可枚举的业务码,客户端据此展示无权限态。
 *
 * @author guardfind
 * @since 2026/11/11
 */
class LeadServiceImplTest extends ContainerlessTestBase {

    private LeadMapper leadMapper;
    private FindCaseMapper findCaseMapper;
    private LeadViewAssembler leadViewAssembler;
    private LeadServiceImpl service;

    @BeforeEach
    void setUp() {
        leadMapper = mock(LeadMapper.class);
        findCaseMapper = mock(FindCaseMapper.class);
        leadViewAssembler = mock(LeadViewAssembler.class);
        service = new LeadServiceImpl(leadMapper, mock(LeadReplyMapper.class), findCaseMapper, mock(AppUserMapper.class), mock(AppMessageService.class), mock(SensitiveWordMatcher.class), mock(LeadRateLimiter.class), leadViewAssembler);
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
}
