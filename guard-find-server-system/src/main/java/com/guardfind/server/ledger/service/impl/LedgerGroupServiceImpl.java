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

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.exception.AppBizException;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.ledger.mapper.LedgerGroupMapper;
import com.guardfind.server.ledger.mapper.LedgerGroupMemberMapper;
import com.guardfind.server.ledger.mapper.LedgerRecordMapper;
import com.guardfind.server.ledger.model.entity.LedgerGroupDO;
import com.guardfind.server.ledger.model.entity.LedgerRecordDO;
import com.guardfind.server.ledger.model.entity.LedgerGroupMemberDO;
import com.guardfind.server.ledger.model.req.LedgerGroupReq;
import com.guardfind.server.ledger.model.req.LedgerJoinReq;
import com.guardfind.server.ledger.model.resp.LedgerGroupMemberResp;
import com.guardfind.server.ledger.model.resp.LedgerGroupResp;
import com.guardfind.server.ledger.service.LedgerGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.starter.core.exception.BusinessException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 家庭组实现。权限口径:
 * 组长 = owner_user_id;邀请码仅组长可见;移除成员/解散仅组长;
 * 组长不能退出(一期不提供转让);账单归属校验用「是否组成员」判定。
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Service
@RequiredArgsConstructor
public class LedgerGroupServiceImpl implements LedgerGroupService {

    private static final int ROLE_OWNER = 1;
    private static final int ROLE_MEMBER = 2;
    private static final int MAX_MEMBERS = 12;

    private final LedgerGroupMapper groupMapper;
    private final LedgerGroupMemberMapper memberMapper;
    private final LedgerRecordMapper recordMapper;
    private final AppUserMapper appUserMapper;

    @Override
    public LedgerGroupResp create(LedgerGroupReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        LedgerGroupDO group = new LedgerGroupDO();
        group.setName(StrUtil.trim(req.getName()));
        group.setOwnerUserId(userId);
        group.setInviteCode(generateInviteCode());
        groupMapper.insert(group);
        LedgerGroupMemberDO member = new LedgerGroupMemberDO();
        member.setGroupId(group.getId());
        member.setUserId(userId);
        member.setRole(ROLE_OWNER);
        memberMapper.insert(member);
        LedgerGroupResp resp = toResp(group, ROLE_OWNER, 1L);
        return resp;
    }

    @Override
    public List<LedgerGroupResp> myGroups() {
        Long userId = AppUserContextHolder.getUserIdRequired();
        List<LedgerGroupMemberDO> memberships = memberMapper.selectList(Wrappers.<LedgerGroupMemberDO>lambdaQuery()
            .eq(LedgerGroupMemberDO::getUserId, userId));
        if (memberships.isEmpty()) {
            return List.of();
        }
        List<Long> groupIds = memberships.stream().map(LedgerGroupMemberDO::getGroupId).toList();
        Map<Long, LedgerGroupDO> groups = groupMapper.selectByIds(groupIds)
            .stream()
            .collect(Collectors.toMap(LedgerGroupDO::getId, Function.identity()));
        Map<Long, Long> counts = memberMapper.selectList(Wrappers.<LedgerGroupMemberDO>lambdaQuery()
            .in(LedgerGroupMemberDO::getGroupId, groupIds))
            .stream()
            .collect(Collectors.groupingBy(LedgerGroupMemberDO::getGroupId, Collectors.counting()));
        return memberships.stream()
            .filter(membership -> groups.containsKey(membership.getGroupId()))
            .map(membership -> {
                LedgerGroupDO group = groups.get(membership.getGroupId());
                return toResp(group, membership.getRole(), counts.getOrDefault(group.getId(), 1L));
            })
            .toList();
    }

    @Override
    public LedgerGroupResp join(LedgerJoinReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        if (StrUtil.isBlank(req.getInviteCode())) {
            throw new BusinessException("邀请码无效,请核对后重试");
        }
        LedgerGroupDO group = groupMapper.selectOne(Wrappers.<LedgerGroupDO>lambdaQuery()
            .eq(LedgerGroupDO::getInviteCode, req.getInviteCode().trim().toUpperCase())
            .last("LIMIT 1"));
        if (group == null) {
            throw new BusinessException("邀请码无效,请核对后重试");
        }
        LedgerGroupMemberDO existing = memberMapper.selectOne(Wrappers.<LedgerGroupMemberDO>lambdaQuery()
            .eq(LedgerGroupMemberDO::getGroupId, group.getId())
            .eq(LedgerGroupMemberDO::getUserId, userId)
            .last("LIMIT 1"));
        if (existing != null) {
            return toResp(group, existing.getRole(), memberCount(group.getId()));
        }
        if (memberCount(group.getId()) >= MAX_MEMBERS) {
            throw new BusinessException("该家庭组成员已满(最多 12 人)");
        }
        LedgerGroupMemberDO member = new LedgerGroupMemberDO();
        member.setGroupId(group.getId());
        member.setUserId(userId);
        member.setRole(ROLE_MEMBER);
        memberMapper.insert(member);
        return toResp(group, ROLE_MEMBER, memberCount(group.getId()));
    }

    @Override
    public LedgerGroupResp regenerateInviteCode(Long groupId) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        LedgerGroupDO group = requireOwner(groupId, userId);
        group.setInviteCode(generateInviteCode());
        groupMapper.updateById(group);
        return toResp(group, ROLE_OWNER, memberCount(groupId));
    }

    @Override
    public List<LedgerGroupMemberResp> members(Long groupId) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        requireMembership(groupId, userId);
        List<LedgerGroupMemberDO> memberships = memberMapper.selectList(Wrappers.<LedgerGroupMemberDO>lambdaQuery()
            .eq(LedgerGroupMemberDO::getGroupId, groupId)
            .orderByAsc(LedgerGroupMemberDO::getRole));
        List<Long> userIds = memberships.stream().map(LedgerGroupMemberDO::getUserId).toList();
        Map<Long, AppUserDO> users = userIds.isEmpty()
            ? Map.of()
            : appUserMapper.selectByIds(userIds)
                .stream()
                .collect(Collectors.toMap(AppUserDO::getId, Function.identity()));
        return memberships.stream().map(membership -> {
            LedgerGroupMemberResp resp = new LedgerGroupMemberResp();
            resp.setUserId(membership.getUserId());
            resp.setRole(membership.getRole());
            resp.setCreateTime(membership.getCreateTime());
            AppUserDO user = users.get(membership.getUserId());
            if (user != null) {
                resp.setNickname(user.getNickname());
                resp.setAvatar(user.getAvatar());
            }
            return resp;
        }).toList();
    }

    @Override
    public void removeMember(Long groupId, Long memberUserId) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        LedgerGroupDO group = requireOwner(groupId, userId);
        if (Objects.equals(memberUserId, group.getOwnerUserId())) {
            throw new BusinessException("组长不能被移除");
        }
        memberMapper.delete(Wrappers.<LedgerGroupMemberDO>lambdaQuery()
            .eq(LedgerGroupMemberDO::getGroupId, groupId)
            .eq(LedgerGroupMemberDO::getUserId, memberUserId));
    }

    @Override
    public void quit(Long groupId) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        LedgerGroupDO group = requireMembership(groupId, userId);
        if (Objects.equals(group.getOwnerUserId(), userId)) {
            throw new BusinessException("组长不能退出,如不再使用请解散家庭组");
        }
        memberMapper.delete(Wrappers.<LedgerGroupMemberDO>lambdaQuery()
            .eq(LedgerGroupMemberDO::getGroupId, groupId)
            .eq(LedgerGroupMemberDO::getUserId, userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dissolve(Long groupId) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        LedgerGroupDO group = requireOwner(groupId, userId);
        // 组内账单归还原主个人账本,数据不丢;UpdateWrapper 字符串列名,便于纯单测环境(无 lambda cache)
        recordMapper.update(null, Wrappers.<LedgerRecordDO>update().eq("group_id", groupId).set("group_id", null));
        memberMapper.delete(Wrappers.<LedgerGroupMemberDO>lambdaQuery().eq(LedgerGroupMemberDO::getGroupId, groupId));
        groupMapper.deleteById(group.getId());
    }

    @Override
    public LedgerGroupDO requireMembership(Long groupId) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        return requireMembership(groupId, userId);
    }

    @Override
    public void requireUserInGroup(Long groupId, Long userId) {
        requireMembership(groupId, userId);
    }

    private LedgerGroupDO requireMembership(Long groupId, Long userId) {
        LedgerGroupDO group = groupId == null ? null : groupMapper.selectById(groupId);
        if (group == null) {
            throw new AppBizException(AppBizException.NOT_FOUND, "家庭组不存在");
        }
        Long count = memberMapper.selectCount(Wrappers.<LedgerGroupMemberDO>lambdaQuery()
            .eq(LedgerGroupMemberDO::getGroupId, groupId)
            .eq(LedgerGroupMemberDO::getUserId, userId));
        if (count == 0) {
            // 非成员与不存在同一话术,不泄露组存在性
            throw new AppBizException(AppBizException.NOT_FOUND, "家庭组不存在");
        }
        return group;
    }

    private LedgerGroupDO requireOwner(Long groupId, Long userId) {
        LedgerGroupDO group = requireMembership(groupId, userId);
        if (!Objects.equals(group.getOwnerUserId(), userId)) {
            throw new AppBizException(AppBizException.RULE_REJECTED, "仅组长可以执行此操作");
        }
        return group;
    }

    private long memberCount(Long groupId) {
        return memberMapper.selectCount(Wrappers.<LedgerGroupMemberDO>lambdaQuery()
            .eq(LedgerGroupMemberDO::getGroupId, groupId));
    }

    private String generateInviteCode() {
        // 冲突重试:6 位码空间 36^6,小用户量下冲突概率极低
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = RandomUtil.randomStringUpper(6);
            Long count = groupMapper.selectCount(Wrappers.<LedgerGroupDO>lambdaQuery()
                .eq(LedgerGroupDO::getInviteCode, code));
            if (count == 0) {
                return code;
            }
        }
        throw new BusinessException("邀请码生成失败,请重试");
    }

    private LedgerGroupResp toResp(LedgerGroupDO group, int myRole, long memberCount) {
        LedgerGroupResp resp = new LedgerGroupResp();
        resp.setId(group.getId());
        resp.setName(group.getName());
        resp.setOwnerUserId(group.getOwnerUserId());
        resp.setMyRole(myRole);
        resp.setMemberCount(memberCount);
        // 邀请码仅组长可见
        if (myRole == ROLE_OWNER) {
            resp.setInviteCode(group.getInviteCode());
        }
        return resp;
    }
}
