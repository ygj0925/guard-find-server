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

package com.guardfind.server.notification.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.guardfind.server.notification.converter.MessageConverter;
import com.guardfind.server.notification.enums.NotificationEventEnum;
import com.guardfind.server.notification.mapper.AppMessageMapper;
import com.guardfind.server.notification.mapper.PreferenceMapper;
import com.guardfind.server.notification.model.entity.AppMessageDO;
import com.guardfind.server.notification.model.entity.PreferenceDO;
import com.guardfind.server.notification.model.query.AppMessageQuery;
import com.guardfind.server.notification.model.req.PreferenceItemReq;
import com.guardfind.server.notification.model.req.PreferenceUpdateReq;
import com.guardfind.server.notification.model.resp.MarkReadResp;
import com.guardfind.server.notification.model.resp.MessagePageResp;
import com.guardfind.server.notification.model.resp.AppMessageResp;
import com.guardfind.server.notification.model.resp.PreferenceListResp;
import com.guardfind.server.notification.model.resp.PreferenceResp;
import com.guardfind.server.notification.model.resp.UnreadResp;
import com.guardfind.server.notification.service.AppMessageService;
import com.guardfind.server.notification.service.NotificationQueryService;
import com.guardfind.server.notification.support.PreferenceSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.starter.core.util.CollUtils;
import top.continew.starter.core.util.validation.ValidationUtils;
import top.continew.starter.extension.crud.model.query.PageQuery;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * C 端消息与偏好查询业务实现
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationQueryServiceImpl implements NotificationQueryService {

    /**
     * 单次全部标记已读的最大处理条数（超出部分留待下次请求，避免一次 UPDATE 锁住大量行）
     */
    private static final int MARK_ALL_BATCH_SIZE = 500;

    /**
     * 合法消息分组（由事件目录派生，不在代码里另立一套枚举）
     */
    private static final Set<String> CATEGORIES = Arrays.stream(NotificationEventEnum.values())
        .map(NotificationEventEnum::getCategory)
        .collect(Collectors.toUnmodifiableSet());

    private final AppMessageMapper messageMapper;
    private final PreferenceMapper preferenceMapper;
    private final AppMessageService appMessageService;

    @Override
    public UnreadResp countUnread(Long userId) {
        ValidationUtils.throwIfNull(userId, "请先登录");
        return new UnreadResp(appMessageService.countUnread(userId));
    }

    @Override
    public MessagePageResp page(AppMessageQuery query, PageQuery pageQuery, Long userId) {
        ValidationUtils.throwIfNull(userId, "请先登录");
        AppMessageQuery condition = query == null ? new AppMessageQuery() : query;
        String category = StrUtil.trimToNull(condition.getCategory());
        ValidationUtils.throwIf(category != null && !CATEGORIES.contains(category), "消息分组 [{}] 不合法", category);
        PageQuery page = pageQuery == null ? new PageQuery(1, 10) : pageQuery;
        IPage<AppMessageDO> messagePage = messageMapper.selectPage(new Page<>(page.getPage(), page.getSize()), Wrappers
            .<AppMessageDO>lambdaQuery()
            .eq(AppMessageDO::getReceiverId, userId)
            .eq(Boolean.TRUE.equals(condition.getOnlyUnread()), AppMessageDO::getReadStatus, false)
            .eq(category != null, AppMessageDO::getCategory, category)
            .orderByDesc(AppMessageDO::getCreateTime)
            .orderByDesc(AppMessageDO::getId));
        List<AppMessageResp> records = MessageConverter.toRespList(messagePage.getRecords());
        return MessagePageResp.of(records, messagePage.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long id, Long userId) {
        ValidationUtils.throwIfNull(userId, "请先登录");
        ValidationUtils.throwIfNull(id, "消息 ID 不能为空");
        AppMessageDO message = messageMapper.selectById(id);
        // 他人消息与不存在消息返回同一提示，避免通过错误信息探测消息是否存在
        ValidationUtils.throwIf(message == null || !Objects.equals(message.getReceiverId(), userId), "消息不存在");
        if (Boolean.TRUE.equals(message.getReadStatus())) {
            // 幂等：重复标记已读不再产生写放大
            return;
        }
        this.doMarkRead(CollUtil.newArrayList(id), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MarkReadResp markAllRead(Long userId) {
        ValidationUtils.throwIfNull(userId, "请先登录");
        List<AppMessageDO> unreadList = messageMapper.selectList(Wrappers.<AppMessageDO>lambdaQuery()
            .select(AppMessageDO::getId)
            .eq(AppMessageDO::getReceiverId, userId)
            .eq(AppMessageDO::getReadStatus, false)
            .orderByAsc(AppMessageDO::getCreateTime)
            .last("LIMIT " + MARK_ALL_BATCH_SIZE));
        if (CollUtil.isEmpty(unreadList)) {
            return new MarkReadResp(0);
        }
        List<Long> ids = CollUtils.mapToList(unreadList, AppMessageDO::getId);
        this.doMarkRead(ids, userId);
        return new MarkReadResp(ids.size());
    }

    @Override
    public PreferenceListResp listPreference(Long userId) {
        ValidationUtils.throwIfNull(userId, "请先登录");
        Map<String, PreferenceDO> preferenceMap = this.findPreferenceMap(userId);
        List<PreferenceResp> items = PreferenceSupport.catalog()
            .stream()
            .map(event -> PreferenceSupport.toResp(event, preferenceMap.get(event.name())))
            .toList();
        return new PreferenceListResp(items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePreference(PreferenceUpdateReq req, Long userId) {
        ValidationUtils.throwIfNull(userId, "请先登录");
        ValidationUtils.throwIfNull(req, "请提交要设置的通知偏好");
        ValidationUtils.throwIf(CollUtil.isEmpty(req.getItems()), "请至少设置一项通知偏好");
        LocalDateTime now = LocalDateTime.now();
        Map<String, PreferenceDO> preferenceMap = this.findPreferenceMap(userId);
        for (PreferenceItemReq item : req.getItems()) {
            NotificationEventEnum event = PreferenceSupport.resolveEvent(item.getType());
            ValidationUtils.throwIfNull(event, "通知类型 [{}] 不合法", item.getType());
            boolean inboxEnabled = Boolean.TRUE.equals(item.getInboxEnabled());
            boolean smsEnabled = Boolean.TRUE.equals(item.getSmsEnabled());
            // 审核结果类消息必须可达：拒绝关闭站内信的偏好写入，并给出可展示的说明
            ValidationUtils.throwIf(event.isInboxForced() && !inboxEnabled, "「{}」为重要结果通知，不支持关闭站内信", event.getLabel());
            PreferenceDO exist = preferenceMap.get(event.name());
            if (exist == null) {
                PreferenceDO preference = new PreferenceDO();
                preference.setUserId(userId);
                preference.setType(event.name());
                preference.setInboxEnabled(inboxEnabled);
                preference.setSmsEnabled(smsEnabled);
                preference.setCreateUser(userId);
                preference.setCreateTime(now);
                preference.setUpdateUser(userId);
                preference.setUpdateTime(now);
                preferenceMapper.insert(preference);
                continue;
            }
            preferenceMapper.update(null, Wrappers.<PreferenceDO>lambdaUpdate()
                .set(PreferenceDO::getInboxEnabled, inboxEnabled)
                .set(PreferenceDO::getSmsEnabled, smsEnabled)
                .set(PreferenceDO::getUpdateUser, userId)
                .set(PreferenceDO::getUpdateTime, now)
                .eq(PreferenceDO::getId, exist.getId())
                .eq(PreferenceDO::getUserId, userId));
        }
        log.info("通知偏好已更新，userId={}，项数={}", userId, req.getItems().size());
    }

    /**
     * 批量置为已读（更新条件同时带 receiver_id，杜绝越权改写他人消息）
     *
     * @param ids    消息 ID 列表
     * @param userId 当前登录用户 ID
     */
    void doMarkRead(List<Long> ids, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        int updated = messageMapper.update(null, Wrappers.<AppMessageDO>lambdaUpdate()
            .set(AppMessageDO::getReadStatus, true)
            .set(AppMessageDO::getReadTime, now)
            .set(AppMessageDO::getUpdateUser, userId)
            .set(AppMessageDO::getUpdateTime, now)
            .in(AppMessageDO::getId, ids)
            .eq(AppMessageDO::getReceiverId, userId)
            .eq(AppMessageDO::getReadStatus, false));
        if (updated == 0) {
            log.debug("标记已读未影响任何行（可能已读或不属于该用户），userId={}，ids={}", userId, ids);
        }
    }

    /**
     * 查询该用户的全部偏好（type -> 记录）
     *
     * @param userId 用户 ID
     * @return 偏好映射
     */
    Map<String, PreferenceDO> findPreferenceMap(Long userId) {
        List<PreferenceDO> list = preferenceMapper.selectList(Wrappers.<PreferenceDO>lambdaQuery()
            .eq(PreferenceDO::getUserId, userId));
        if (CollUtil.isEmpty(list)) {
            return Map.of();
        }
        return list.stream().collect(Collectors.toMap(PreferenceDO::getType, p -> p, (a, b) -> a));
    }
}
