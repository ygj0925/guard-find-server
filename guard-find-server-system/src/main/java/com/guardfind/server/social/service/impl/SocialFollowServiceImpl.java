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

package com.guardfind.server.social.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.notification.enums.NotificationEventEnum;
import com.guardfind.server.notification.service.AppMessageService;
import com.guardfind.server.post.mapper.PostMapper;
import com.guardfind.server.social.mapper.SocialFollowMapper;
import com.guardfind.server.social.model.entity.SocialFollowDO;
import com.guardfind.server.social.model.resp.SocialCountsResp;
import com.guardfind.server.social.model.resp.SocialUserBriefResp;
import com.guardfind.server.social.service.SocialFollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 关注关系实现。
 * 口径:不能关注自己;关注幂等(重复关注返回 false 不发通知);
 * 列表批量补用户信息与"我是否已关注"标记,避免 N+1;
 * 新关注向对方发"新粉丝"站内信(复用现有消息中心)。
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Service
@RequiredArgsConstructor
public class SocialFollowServiceImpl implements SocialFollowService {

    private final SocialFollowMapper followMapper;
    private final AppUserMapper appUserMapper;
    private final PostMapper postMapper;
    private final AppMessageService messageService;

    @Override
    public boolean follow(Long targetUserId) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        if (Objects.equals(userId, targetUserId)) {
            throw new BusinessException("不能关注自己");
        }
        Long count = followMapper.selectCount(Wrappers.<SocialFollowDO>lambdaQuery()
            .eq(SocialFollowDO::getFollowerId, userId)
            .eq(SocialFollowDO::getFollowingId, targetUserId));
        if (count > 0) {
            return false;
        }
        SocialFollowDO follow = new SocialFollowDO();
        follow.setFollowerId(userId);
        follow.setFollowingId(targetUserId);
        followMapper.insert(follow);
        messageService
            .send(targetUserId, NotificationEventEnum.SOCIAL_FOLLOWED, "SOCIAL", userId, "新粉丝", "有人关注了你,去看看 TA 是谁");
        return true;
    }

    @Override
    public void unfollow(Long targetUserId) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        followMapper.delete(Wrappers.<SocialFollowDO>lambdaQuery()
            .eq(SocialFollowDO::getFollowerId, userId)
            .eq(SocialFollowDO::getFollowingId, targetUserId));
    }

    @Override
    public boolean isFollowing(Long targetUserId) {
        Long userId = AppUserContextHolder.getUserId();
        if (userId == null) {
            return false;
        }
        return followMapper.selectCount(Wrappers.<SocialFollowDO>lambdaQuery()
            .eq(SocialFollowDO::getFollowerId, userId)
            .eq(SocialFollowDO::getFollowingId, targetUserId)) > 0;
    }

    @Override
    public PageResp<SocialUserBriefResp> followingPage(PageQuery pageQuery) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        return pageBy(userId, true, pageQuery);
    }

    @Override
    public PageResp<SocialUserBriefResp> followerPage(PageQuery pageQuery) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        return pageBy(userId, false, pageQuery);
    }

    @Override
    public SocialCountsResp counts(Long targetUserId) {
        SocialCountsResp resp = new SocialCountsResp();
        resp.setFollowingCount(followMapper.selectCount(Wrappers.<SocialFollowDO>lambdaQuery()
            .eq(SocialFollowDO::getFollowerId, targetUserId)));
        resp.setFollowerCount(followMapper.selectCount(Wrappers.<SocialFollowDO>lambdaQuery()
            .eq(SocialFollowDO::getFollowingId, targetUserId)));
        resp.setPostCount(postMapper.selectCount(Wrappers.<com.guardfind.server.post.model.entity.PostDO>lambdaQuery()
            .eq(com.guardfind.server.post.model.entity.PostDO::getUserId, targetUserId)
            .eq(com.guardfind.server.post.model.entity.PostDO::getStatus, 1)));
        resp.setFollowedByMe(isFollowing(targetUserId));
        return resp;
    }

    /**
     * 关注/粉丝列表:direction=true 关注(我→别人),false 粉丝(别人→我)。
     * 批量补昵称头像 + followedByMe 标记。
     */
    private PageResp<SocialUserBriefResp> pageBy(Long userId, boolean following, PageQuery pageQuery) {
        Page<SocialFollowDO> page = followMapper
            .selectPage(new Page<>(pageNum(pageQuery), pageSize(pageQuery)), Wrappers.<SocialFollowDO>lambdaQuery()
                .eq(following, SocialFollowDO::getFollowerId, userId)
                .eq(!following, SocialFollowDO::getFollowingId, userId)
                .orderByDesc(SocialFollowDO::getId));
        List<SocialFollowDO> relations = page.getRecords();
        List<Long> otherIds = relations.stream()
            .map(rel -> following ? rel.getFollowingId() : rel.getFollowerId())
            .toList();
        Map<Long, AppUserDO> users = otherIds.isEmpty()
            ? Map.of()
            : appUserMapper.selectByIds(otherIds)
                .stream()
                .collect(Collectors.toMap(AppUserDO::getId, Function.identity()));
        Set<Long> followedByMeIds = otherIds.isEmpty()
            ? Set.of()
            : followMapper.selectList(Wrappers.<SocialFollowDO>lambdaQuery()
                .eq(SocialFollowDO::getFollowerId, userId)
                .in(SocialFollowDO::getFollowingId, otherIds))
                .stream()
                .map(SocialFollowDO::getFollowingId)
                .collect(Collectors.toSet());
        List<SocialUserBriefResp> list = relations.stream().map(rel -> {
            Long otherId = following ? rel.getFollowingId() : rel.getFollowerId();
            SocialUserBriefResp resp = new SocialUserBriefResp();
            resp.setUserId(otherId);
            resp.setCreateTime(rel.getCreateTime());
            resp.setFollowedByMe(followedByMeIds.contains(otherId));
            AppUserDO user = users.get(otherId);
            if (user != null) {
                resp.setNickname(user.getNickname());
                resp.setAvatar(user.getAvatar());
            }
            return resp;
        }).toList();
        PageResp<SocialUserBriefResp> resp = new PageResp<>();
        resp.setList(list);
        resp.setTotal(page.getTotal());
        return resp;
    }

    private int pageNum(PageQuery pageQuery) {
        return pageQuery == null || pageQuery.getPage() == null ? 1 : pageQuery.getPage();
    }

    private int pageSize(PageQuery pageQuery) {
        return pageQuery == null || pageQuery.getSize() == null ? 20 : pageQuery.getSize();
    }
}
