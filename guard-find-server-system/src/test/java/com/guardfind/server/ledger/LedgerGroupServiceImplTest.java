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

package com.guardfind.server.ledger;

import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.exception.AppBizException;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.ledger.mapper.LedgerGroupMapper;
import com.guardfind.server.ledger.mapper.LedgerGroupMemberMapper;
import com.guardfind.server.ledger.mapper.LedgerRecordMapper;
import com.guardfind.server.ledger.model.entity.LedgerGroupDO;
import com.guardfind.server.ledger.model.entity.LedgerGroupMemberDO;
import com.guardfind.server.ledger.model.req.LedgerGroupReq;
import com.guardfind.server.ledger.model.req.LedgerJoinReq;
import com.guardfind.server.ledger.model.resp.LedgerGroupResp;
import com.guardfind.server.ledger.service.impl.LedgerGroupServiceImpl;
import com.guardfind.server.support.ContainerlessTestBase;
import top.continew.starter.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LedgerGroupServiceImpl} 单测:组长权限、邀请码加入幂等、退出/解散规则。
 *
 * @author guardfind
 * @since 2026/10/29
 */
class LedgerGroupServiceImplTest extends ContainerlessTestBase {

    private LedgerGroupMapper groupMapper;
    private LedgerGroupMemberMapper memberMapper;
    private LedgerRecordMapper recordMapper;
    private AppUserMapper appUserMapper;
    private LedgerGroupServiceImpl service;

    @BeforeEach
    void setUp() {
        groupMapper = mock(LedgerGroupMapper.class);
        memberMapper = mock(LedgerGroupMemberMapper.class);
        recordMapper = mock(LedgerRecordMapper.class);
        appUserMapper = mock(AppUserMapper.class);
        service = new LedgerGroupServiceImpl(groupMapper, memberMapper, recordMapper, appUserMapper);
    }

    private static LedgerGroupDO group(Long id, Long ownerId, String inviteCode) {
        LedgerGroupDO group = new LedgerGroupDO();
        group.setId(id);
        group.setName("我们的家");
        group.setOwnerUserId(ownerId);
        group.setInviteCode(inviteCode);
        return group;
    }

    private static LedgerJoinReq joinReq() {
        LedgerJoinReq req = new LedgerJoinReq();
        req.setInviteCode("A1B2C3");
        return req;
    }

    private static LedgerGroupMemberDO membership(Long groupId, Long userId, int role) {
        LedgerGroupMemberDO member = new LedgerGroupMemberDO();
        member.setGroupId(groupId);
        member.setUserId(userId);
        member.setRole(role);
        return member;
    }

    private void stubMembership(Long groupId, Long userId, boolean isMember) {
        when(memberMapper.selectCount(any())).thenReturn(isMember ? 1L : 0L);
    }

    @Test
    @DisplayName("创建组:插入组 + 组长成员关系,角色=组长,邀请码仅组长可见")
    void createInsertsOwnerMembership() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(groupMapper.insert(any(LedgerGroupDO.class))).thenAnswer(invocation -> {
                LedgerGroupDO saved = invocation.getArgument(0);
                saved.setId(1L);
                return 1;
            });
            when(memberMapper.insert(any(LedgerGroupMemberDO.class))).thenReturn(1);
            when(memberMapper.selectCount(any())).thenReturn(1L);

            LedgerGroupReq req = new LedgerGroupReq();
            req.setName("我们的家");
            LedgerGroupResp resp = service.create(req);

            assertEquals(1, resp.getMyRole());
            assertEquals(1L, resp.getMemberCount());
            assertEquals(6, resp.getInviteCode().length());
            ArgumentCaptor<LedgerGroupMemberDO> captor = ArgumentCaptor.forClass(LedgerGroupMemberDO.class);
            verify(memberMapper).insert(captor.capture());
            assertEquals(1, captor.getValue().getRole());
            assertEquals(7L, captor.getValue().getUserId());
        }
    }

    @Test
    @DisplayName("凭码加入:首次插入成员关系,重复加入幂等不重复插")
    void joinIsIdempotent() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(groupMapper.selectOne(any())).thenReturn(group(5L, 8L, "A1B2C3"));
            when(memberMapper.selectCount(any())).thenReturn(1L);

            // 第一次:非成员,插入
            when(memberMapper.selectOne(any())).thenReturn(null);
            when(memberMapper.insert(any(LedgerGroupMemberDO.class))).thenReturn(1);
            LedgerGroupResp first = service.join(joinReq());
            assertEquals(2, first.getMyRole());
            verify(memberMapper, times(1)).insert(any(LedgerGroupMemberDO.class));

            // 第二次:已是成员,幂等不再插入
            when(memberMapper.selectOne(any())).thenReturn(membership(5L, 7L, 2));
            LedgerGroupResp second = service.join(joinReq());
            assertEquals(2, second.getMyRole());
            verify(memberMapper, times(1)).insert(any(LedgerGroupMemberDO.class));
        }
    }

    @Test
    @DisplayName("无效邀请码报错;非成员查成员列表/解散均 NOT_FOUND")
    void membershipGuards() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            // 无效码
            when(groupMapper.selectOne(any())).thenReturn(null);
            assertThrows(BusinessException.class, () -> service.join(joinReq()));
            // 非成员
            when(groupMapper.selectById(5L)).thenReturn(group(5L, 8L, "A1B2C3"));
            when(memberMapper.selectCount(any())).thenReturn(0L);
            AppBizException ex = assertThrows(AppBizException.class, () -> service.members(5L));
            assertEquals(AppBizException.NOT_FOUND, ex.getCode());
            AppBizException ex2 = assertThrows(AppBizException.class, () -> service.dissolve(5L));
            assertEquals(AppBizException.NOT_FOUND, ex2.getCode());
        }
    }

    @Test
    @DisplayName("组长不能退出;成员退出成功;组长移除自己被拒绝")
    void quitAndRemoveRules() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(groupMapper.selectById(5L)).thenReturn(group(5L, 7L, "A1B2C3"));
            stubMembership(5L, 7L, true);

            // 组长退出被拒
            assertThrows(BusinessException.class, () -> service.quit(5L));
            verify(memberMapper, never()).delete(any());

            // 组长移除自己被拒
            assertThrows(BusinessException.class, () -> service.removeMember(5L, 7L));

            // 组长移除普通成员:删除该成员关系
            service.removeMember(5L, 9L);
            verify(memberMapper).delete(any());
        }
    }

    @Test
    @DisplayName("解散:重置组内账单 group_id + 清成员 + 删组")
    void dissolveResetsRecords() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(groupMapper.selectById(5L)).thenReturn(group(5L, 7L, "A1B2C3"));
            stubMembership(5L, 7L, true);

            service.dissolve(5L);

            verify(recordMapper).update(org.mockito.Mockito.isNull(), any());
            verify(memberMapper).delete(any());
            verify(groupMapper).deleteById(5L);
        }
    }

    @Test
    @DisplayName("重新生成邀请码:仅组长,旧码变化")
    void regenerateInviteCode() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(groupMapper.selectById(5L)).thenReturn(group(5L, 8L, "A1B2C3"));
            stubMembership(5L, 7L, true);

            // 非组长被拒
            AppBizException ex = assertThrows(AppBizException.class, () -> service.regenerateInviteCode(5L));
            assertEquals(AppBizException.RULE_REJECTED, ex.getCode());

            // 组长成功
            when(groupMapper.selectById(5L)).thenReturn(group(5L, 7L, "A1B2C3"));
            LedgerGroupResp resp = service.regenerateInviteCode(5L);
            assertNotEquals("A1B2C3", resp.getInviteCode());
            verify(groupMapper).updateById(any(LedgerGroupDO.class));
        }
    }
}
