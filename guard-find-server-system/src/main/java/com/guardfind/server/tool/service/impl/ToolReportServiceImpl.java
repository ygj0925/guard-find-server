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
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.notification.enums.NotificationEventEnum;
import com.guardfind.server.notification.service.AppMessageService;
import com.guardfind.server.tool.mapper.ToolReportCommentMapper;
import com.guardfind.server.tool.mapper.ToolReportEntryMapper;
import com.guardfind.server.tool.mapper.ToolReportMapper;
import com.guardfind.server.tool.mapper.ToolReportMemberMapper;
import com.guardfind.server.tool.model.entity.ToolReportCommentDO;
import com.guardfind.server.tool.model.entity.ToolReportDO;
import com.guardfind.server.tool.model.entity.ToolReportEntryDO;
import com.guardfind.server.tool.model.entity.ToolReportMemberDO;
import com.guardfind.server.tool.model.req.ToolReportCommentReq;
import com.guardfind.server.tool.model.req.ToolReportCreateReq;
import com.guardfind.server.tool.model.req.ToolReportEntryReq;
import com.guardfind.server.tool.model.resp.ToolReportCommentResp;
import com.guardfind.server.tool.model.resp.ToolReportEntryResp;
import com.guardfind.server.tool.model.resp.ToolReportResp;
import com.guardfind.server.tool.service.ToolReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.starter.core.exception.BusinessException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 月度工作汇报实现。权限矩阵(全部服务端强制,详见接口注释):
 * 非成员 NOT_FOUND;填报仅 REPORTER 本人;草稿仅本人;评论全员;管理仅 OWNER。
 * 互动通知:被评论/被回复走现有消息中心。
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolReportServiceImpl implements ToolReportService {

    private static final int ROLE_OWNER = 1;
    private static final int ROLE_REPORTER = 2;
    private static final int ROLE_VIEWER = 3;

    private final ToolReportMapper reportMapper;
    private final ToolReportMemberMapper memberMapper;
    private final ToolReportEntryMapper entryMapper;
    private final ToolReportCommentMapper commentMapper;
    private final AppUserMapper appUserMapper;
    private final AppMessageService messageService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ToolReportCreateReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        if (StrUtil.isBlank(req.getTitle())) {
            throw new BusinessException("汇报标题不能为空");
        }
        ToolReportDO activity = new ToolReportDO();
        activity.setUserId(userId);
        activity.setTitle(StrUtil.trim(req.getTitle()));
        activity.setPeriod(req.getPeriod().trim());
        activity.setDueDate(req.getDueDate());
        reportMapper.insert(activity);

        // 成员:发起人 OWNER + 填报人 REPORTER + 接收人 VIEWER;一个人只保留一个角色(填报优先)
        insertMember(activity.getId(), userId, ROLE_OWNER);
        for (Long assignee : dedupeExcept(req.getAssigneeUserIds(), userId)) {
            insertMember(activity.getId(), assignee, ROLE_REPORTER);
            messageService.send(assignee, NotificationEventEnum.SOCIAL_FOLLOWED, "REPORT", activity
                .getId(), "新的工作汇报", "你被指定填写「" + activity.getTitle() + "」,请及时填报");
        }
        for (Long viewer : dedupeExcept(req.getViewerUserIds(), userId)) {
            insertMember(activity.getId(), viewer, ROLE_VIEWER);
        }
        return activity.getId();
    }

    @Override
    public List<ToolReportResp> myActivities() {
        Long userId = AppUserContextHolder.getUserIdRequired();
        List<ToolReportMemberDO> memberships = memberMapper.selectList(Wrappers.<ToolReportMemberDO>lambdaQuery()
            .eq(ToolReportMemberDO::getUserId, userId));
        if (memberships.isEmpty()) {
            return List.of();
        }
        Map<Long, ToolReportMemberDO> myRoleByActivity = memberships.stream()
            .collect(Collectors.toMap(ToolReportMemberDO::getActivityId, Function.identity(), (a, b) -> a));
        List<ToolReportDO> activities = reportMapper.selectByIds(myRoleByActivity.keySet())
            .stream()
            .sorted((a, b) -> b.getId().compareTo(a.getId()))
            .toList();
        List<ToolReportEntryDO> myEntries = entryMapper.selectList(Wrappers.<ToolReportEntryDO>lambdaQuery()
            .eq(ToolReportEntryDO::getUserId, userId)
            .in(ToolReportEntryDO::getActivityId, myRoleByActivity.keySet()));
        Map<Long, ToolReportEntryDO> myEntryByActivity = myEntries.stream()
            .collect(Collectors.toMap(ToolReportEntryDO::getActivityId, Function.identity(), (a, b) -> a));
        return activities.stream()
            .map(activity -> toResp(activity, myRoleByActivity.get(activity.getId()), myEntryByActivity.get(activity
                .getId()), userId, false))
            .toList();
    }

    @Override
    public ToolReportResp detail(Long id) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ToolReportDO activity = getActivity(id);
        ToolReportMemberDO myMembership = requireMembership(id, userId);
        return toResp(activity, myMembership, null, userId, true);
    }

    @Override
    public ToolReportEntryResp entry(Long id, Long entryUserId) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ToolReportDO activity = getActivity(id);
        requireMembership(id, userId);
        // 草稿仅本人;他人只能读已提交
        boolean self = Objects.equals(userId, entryUserId);
        ToolReportEntryDO entry = entryMapper.selectOne(Wrappers.<ToolReportEntryDO>lambdaQuery()
            .eq(ToolReportEntryDO::getActivityId, id)
            .eq(ToolReportEntryDO::getUserId, entryUserId)
            .last("LIMIT 1"));
        if (entry != null && entry.getStatus() != 2 && !self) {
            entry = null;
        }
        AppUserDO author = entryUserId == null ? null : appUserMapper.selectById(entryUserId);
        ToolReportEntryResp resp = new ToolReportEntryResp();
        resp.setActivityId(id);
        resp.setActivityTitle(activity.getTitle());
        resp.setPeriod(activity.getPeriod());
        resp.setUserId(entryUserId);
        if (author != null) {
            resp.setNickname(author.getNickname());
            resp.setAvatar(author.getAvatar());
        }
        resp.setContent(entry == null ? null : entry.getContent());
        resp.setStatus(entry == null ? 0 : entry.getStatus());
        resp.setSubmittedAt(entry == null ? null : entry.getSubmittedAt());
        resp.setLate(entry != null && entry.getSubmittedAt() != null && activity.getDueDate() != null && entry
            .getSubmittedAt()
            .isAfter(activity.getDueDate()));
        resp.setMyRole(requireMembership(id, userId).getRole());
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveEntry(Long id, ToolReportEntryReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ToolReportDO activity = getActivity(id);
        ToolReportMemberDO membership = requireMembership(id, userId);
        // 仅填报人(或发起人本人兼任填报)可写自己的月报
        if (membership.getRole() != ROLE_REPORTER && !Objects.equals(activity.getUserId(), userId)) {
            throw new AppBizException(AppBizException.RULE_REJECTED, "你的角色不是填报人,无需填报");
        }
        String content = StrUtil.trim(req.getContent());
        if (StrUtil.isBlank(content)) {
            throw new BusinessException("汇报正文不能为空");
        }
        boolean submit = Boolean.TRUE.equals(req.getSubmit());
        if (submit && activity.getDueDate() != null && LocalDateTime.now().isAfter(activity.getDueDate())) {
            // 迟交允许,但记录 submittedAt,前端标"迟交"
            log.info("[report] 迟交提交: activity={}, user={}", id, userId);
        }
        ToolReportEntryDO entry = entryMapper.selectOne(Wrappers.<ToolReportEntryDO>lambdaQuery()
            .eq(ToolReportEntryDO::getActivityId, id)
            .eq(ToolReportEntryDO::getUserId, userId)
            .last("LIMIT 1"));
        if (entry == null) {
            entry = new ToolReportEntryDO();
            entry.setActivityId(id);
            entry.setUserId(userId);
        }
        entry.setContent(content);
        entry.setStatus(submit ? 2 : 1);
        entry.setSubmittedAt(submit ? LocalDateTime.now() : null);
        if (entry.getId() == null) {
            entryMapper.insert(entry);
        } else {
            entryMapper.updateById(entry);
        }
    }

    @Override
    public List<ToolReportCommentResp> comments(Long id, Long entryUserId) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        requireMembership(id, userId);
        ToolReportEntryDO entry = entryMapper.selectOne(Wrappers.<ToolReportEntryDO>lambdaQuery()
            .eq(ToolReportEntryDO::getActivityId, id)
            .eq(ToolReportEntryDO::getUserId, entryUserId)
            .last("LIMIT 1"));
        if (entry == null) {
            return List.of();
        }
        List<ToolReportCommentDO> comments = commentMapper.selectList(Wrappers.<ToolReportCommentDO>lambdaQuery()
            .eq(ToolReportCommentDO::getEntryId, entry.getId())
            .orderByAsc(ToolReportCommentDO::getId));
        List<Long> userIds = comments.stream().map(ToolReportCommentDO::getUserId).distinct().toList();
        Map<Long, AppUserDO> users = userIds.isEmpty()
            ? Map.of()
            : appUserMapper.selectByIds(userIds)
                .stream()
                .collect(Collectors.toMap(AppUserDO::getId, Function.identity()));
        return comments.stream().map(comment -> {
            ToolReportCommentResp resp = new ToolReportCommentResp();
            resp.setId(comment.getId());
            resp.setUserId(comment.getUserId());
            resp.setContent(comment.getContent());
            resp.setReplyToCommentId(comment.getReplyToCommentId());
            resp.setReplyToUserId(comment.getReplyToUserId());
            resp.setCreateTime(comment.getCreateTime());
            AppUserDO user = users.get(comment.getUserId());
            if (user != null) {
                resp.setNickname(user.getNickname());
                resp.setAvatar(user.getAvatar());
            }
            return resp;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long comment(Long id, Long entryUserId, ToolReportCommentReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ToolReportDO activity = getActivity(id);
        requireMembership(id, userId);
        ToolReportEntryDO entry = entryMapper.selectOne(Wrappers.<ToolReportEntryDO>lambdaQuery()
            .eq(ToolReportEntryDO::getActivityId, id)
            .eq(ToolReportEntryDO::getUserId, entryUserId)
            .last("LIMIT 1"));
        if (entry == null) {
            throw new BusinessException("该月报尚未创建,无法评论");
        }
        Long replyToUserId = null;
        if (req.getReplyToCommentId() != null) {
            ToolReportCommentDO target = commentMapper.selectById(req.getReplyToCommentId());
            if (target == null || !Objects.equals(target.getEntryId(), entry.getId())) {
                throw new BusinessException("回复的评论不存在");
            }
            replyToUserId = target.getUserId();
        }
        ToolReportCommentDO comment = new ToolReportCommentDO();
        comment.setEntryId(entry.getId());
        comment.setUserId(userId);
        comment.setContent(StrUtil.trim(req.getContent()));
        comment.setReplyToCommentId(req.getReplyToCommentId());
        comment.setReplyToUserId(replyToUserId);
        commentMapper.insert(comment);
        // 互动通知:月报作者收到评论;楼中楼另通知被回复人(排除自己)
        if (!Objects.equals(entry.getUserId(), userId)) {
            messageService.send(entry.getUserId(), NotificationEventEnum.POST_COMMENTED, "REPORT", entry
                .getId(), "月报收到评论", "你的「" + activity.getTitle() + "」有新评论,去查看");
        }
        if (replyToUserId != null && !Objects.equals(replyToUserId, userId) && !Objects.equals(replyToUserId, entry
            .getUserId())) {
            messageService.send(replyToUserId, NotificationEventEnum.POST_COMMENTED, "REPORT", entry
                .getId(), "评论收到回复", "你的评论收到回复,去查看");
        }
        return comment.getId();
    }

    @Override
    public void addMembers(Long id, List<Long> userIds, Integer role) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        requireOwner(id, userId);
        if (userIds == null || userIds.isEmpty()) {
            throw new BusinessException("成员列表不能为空");
        }
        if (role != ROLE_REPORTER && role != ROLE_VIEWER) {
            throw new BusinessException("角色取值不合法");
        }
        for (Long target : dedupeExcept(userIds, userId)) {
            Long exists = memberMapper.selectCount(Wrappers.<ToolReportMemberDO>lambdaQuery()
                .eq(ToolReportMemberDO::getActivityId, id)
                .eq(ToolReportMemberDO::getUserId, target));
            if (exists == 0) {
                insertMember(id, target, role);
            }
        }
    }

    @Override
    public void removeMember(Long id, Long memberUserId) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ToolReportDO activity = requireOwner(id, userId);
        if (Objects.equals(activity.getUserId(), memberUserId)) {
            throw new BusinessException("发起人不能被移除");
        }
        memberMapper.delete(Wrappers.<ToolReportMemberDO>lambdaQuery()
            .eq(ToolReportMemberDO::getActivityId, id)
            .eq(ToolReportMemberDO::getUserId, memberUserId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ToolReportDO activity = requireOwner(id, userId);
        List<ToolReportEntryDO> entries = entryMapper.selectList(Wrappers.<ToolReportEntryDO>lambdaQuery()
            .eq(ToolReportEntryDO::getActivityId, id));
        for (ToolReportEntryDO entry : entries) {
            commentMapper.delete(Wrappers.<ToolReportCommentDO>lambdaQuery()
                .eq(ToolReportCommentDO::getEntryId, entry.getId()));
        }
        entryMapper.delete(Wrappers.<ToolReportEntryDO>lambdaQuery().eq(ToolReportEntryDO::getActivityId, id));
        memberMapper.delete(Wrappers.<ToolReportMemberDO>lambdaQuery().eq(ToolReportMemberDO::getActivityId, id));
        reportMapper.deleteById(activity.getId());
    }

    private void insertMember(Long activityId, Long userId, int role) {
        ToolReportMemberDO member = new ToolReportMemberDO();
        member.setActivityId(activityId);
        member.setUserId(userId);
        member.setRole(role);
        memberMapper.insert(member);
    }

    private ToolReportMemberDO requireMembership(Long activityId, Long userId) {
        ToolReportMemberDO membership = memberMapper.selectOne(Wrappers.<ToolReportMemberDO>lambdaQuery()
            .eq(ToolReportMemberDO::getActivityId, activityId)
            .eq(ToolReportMemberDO::getUserId, userId)
            .last("LIMIT 1"));
        if (membership == null) {
            throw new AppBizException(AppBizException.NOT_FOUND, "汇报活动不存在");
        }
        return membership;
    }

    private ToolReportDO requireOwner(Long activityId, Long userId) {
        ToolReportDO activity = getActivity(activityId);
        if (!Objects.equals(activity.getUserId(), userId)) {
            throw new AppBizException(AppBizException.RULE_REJECTED, "仅发起人可以执行此操作");
        }
        return activity;
    }

    private ToolReportDO getActivity(Long id) {
        if (id == null) {
            throw new BusinessException("汇报活动 ID 不能为空");
        }
        ToolReportDO activity = reportMapper.selectById(id);
        if (activity == null) {
            throw new AppBizException(AppBizException.NOT_FOUND, "汇报活动不存在");
        }
        return activity;
    }

    /** 去重并排除指定用户(发起人不重复出现在填报/接收名单) */
    private List<Long> dedupeExcept(List<Long> ids, Long excluded) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<Long> unique = new LinkedHashSet<>(ids);
        unique.remove(excluded);
        unique.removeIf(Objects::isNull);
        return new ArrayList<>(unique);
    }

    private ToolReportResp toResp(ToolReportDO activity,
                                  ToolReportMemberDO membership,
                                  ToolReportEntryDO myEntry,
                                  Long userId,
                                  boolean withReporters) {
        ToolReportResp resp = new ToolReportResp();
        resp.setId(activity.getId());
        resp.setTitle(activity.getTitle());
        resp.setPeriod(activity.getPeriod());
        resp.setDueDate(activity.getDueDate());
        resp.setMyRole(membership.getRole());
        List<Long> reporterIds = memberMapper.selectList(Wrappers.<ToolReportMemberDO>lambdaQuery()
            .eq(ToolReportMemberDO::getActivityId, activity.getId())
            .eq(ToolReportMemberDO::getRole, ROLE_REPORTER)).stream().map(ToolReportMemberDO::getUserId).toList();
        resp.setReporterCount((long)reporterIds.size());
        List<ToolReportEntryDO> entries = entryMapper.selectList(Wrappers.<ToolReportEntryDO>lambdaQuery()
            .eq(ToolReportEntryDO::getActivityId, activity.getId()));
        long submitted = entries.stream().filter(e -> e.getStatus() != null && e.getStatus() == 2).count();
        resp.setSubmittedCount(submitted);
        ToolReportEntryDO myEntryForActivity = myEntry != null
            ? myEntry
            : entries.stream().filter(e -> Objects.equals(e.getUserId(), userId)).findFirst().orElse(null);
        resp.setSubmittedByMe(myEntryForActivity != null && myEntryForActivity.getStatus() == 2);
        resp.setMyEntryStatus(myEntryForActivity == null ? null : myEntryForActivity.getStatus());
        if (withReporters) {
            Map<Long, ToolReportEntryDO> entryByUser = entries.stream()
                .collect(Collectors.toMap(ToolReportEntryDO::getUserId, Function.identity(), (a, b) -> a));
            List<ToolReportResp.ReporterStatus> reporters = new ArrayList<>();
            for (Long reporterId : reporterIds) {
                ToolReportResp.ReporterStatus item = new ToolReportResp.ReporterStatus();
                item.setUserId(reporterId);
                ToolReportEntryDO entry = entryByUser.get(reporterId);
                item.setEntryStatus(entry == null ? 0 : entry.getStatus());
                item.setLate(entry != null && entry.getSubmittedAt() != null && activity.getDueDate() != null && entry
                    .getSubmittedAt()
                    .isAfter(activity.getDueDate()));
                reporters.add(item);
            }
            List<Long> userIds = reporters.stream().map(ToolReportResp.ReporterStatus::getUserId).toList();
            Map<Long, AppUserDO> users = userIds.isEmpty()
                ? Map.of()
                : appUserMapper.selectByIds(userIds)
                    .stream()
                    .collect(Collectors.toMap(AppUserDO::getId, Function.identity()));
            reporters.forEach(item -> {
                AppUserDO user = users.get(item.getUserId());
                if (user != null) {
                    item.setNickname(user.getNickname());
                    item.setAvatar(user.getAvatar());
                }
            });
            resp.setReporters(reporters);
        }
        return resp;
    }
}
