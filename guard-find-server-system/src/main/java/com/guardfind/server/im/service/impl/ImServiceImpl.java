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

package com.guardfind.server.im.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.exception.AppBizException;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.im.mapper.ImConversationMapper;
import com.guardfind.server.im.mapper.ImMessageMapper;
import com.guardfind.server.im.model.entity.ImConversationDO;
import com.guardfind.server.im.model.entity.ImMessageDO;
import com.guardfind.server.im.model.resp.ImConversationResp;
import com.guardfind.server.im.model.resp.ImMessageResp;
import com.guardfind.server.im.service.ImService;
import com.guardfind.server.notification.enums.NotificationEventEnum;
import com.guardfind.server.notification.service.AppMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.starter.core.exception.BusinessException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * IM 私信实现(轮询方案)。
 * 会话键: user_a_id < user_b_id 保证唯一对;unread_a/b 分别计未读;
 * 发消息时对方收到站内信通知;删除会话仅自己视角隐藏。
 */
@Service
@RequiredArgsConstructor
public class ImServiceImpl implements ImService {

    private final ImConversationMapper conversationMapper;
    private final ImMessageMapper messageMapper;
    private final AppUserMapper appUserMapper;
    private final AppMessageService messageService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImConversationResp getOrCreate(Long targetUserId) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        if (Objects.equals(userId, targetUserId)) {
            throw new BusinessException("不能和自己创建会话");
        }
        Long a = Math.min(userId, targetUserId);
        Long b = Math.max(userId, targetUserId);
        ImConversationDO conv = conversationMapper.selectOne(Wrappers.<ImConversationDO>lambdaQuery()
            .eq(ImConversationDO::getUserAId, a)
            .eq(ImConversationDO::getUserBId, b)
            .last("LIMIT 1"));
        if (conv == null) {
            conv = new ImConversationDO();
            conv.setUserAId(a);
            conv.setUserBId(b);
            conv.setLastMessage(null);
            conv.setLastTime(LocalDateTime.now());
            conv.setUnreadA(0);
            conv.setUnreadB(0);
            conversationMapper.insert(conv);
        }
        return toResp(conv, userId);
    }

    @Override
    public List<ImConversationResp> conversations() {
        Long userId = AppUserContextHolder.getUserIdRequired();
        List<ImConversationDO> convs = conversationMapper.selectList(Wrappers.<ImConversationDO>lambdaQuery()
            .and(w -> w.eq(ImConversationDO::getUserAId, userId).or().eq(ImConversationDO::getUserBId, userId))
            .orderByDesc(ImConversationDO::getLastTime));
        List<Long> peerIds = convs.stream()
            .map(c -> Objects.equals(c.getUserAId(), userId) ? c.getUserBId() : c.getUserAId())
            .distinct()
            .toList();
        Map<Long, AppUserDO> users = peerIds.isEmpty()
            ? Map.of()
            : appUserMapper.selectByIds(peerIds)
                .stream()
                .collect(Collectors.toMap(AppUserDO::getId, Function.identity()));
        return convs.stream().map(conv -> {
            Long peerId = Objects.equals(conv.getUserAId(), userId) ? conv.getUserBId() : conv.getUserAId();
            int unread = Objects.equals(conv.getUserAId(), userId) ? conv.getUnreadA() : conv.getUnreadB();
            ImConversationResp resp = new ImConversationResp();
            resp.setId(conv.getId());
            resp.setPeerUserId(peerId);
            resp.setLastMessage(conv.getLastMessage());
            resp.setLastTime(conv.getLastTime());
            resp.setUnreadCount(unread);
            AppUserDO peer = users.get(peerId);
            if (peer != null) {
                resp.setPeerNickname(peer.getNickname());
                resp.setPeerAvatar(peer.getAvatar());
            }
            return resp;
        }).toList();
    }

    @Override
    public List<ImMessageResp> messages(Long conversationId, Long afterId) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        requireMembership(conversationId, userId);
        // 拉消息同时标记已读
        markRead(conversationId);
        return messageMapper.selectList(Wrappers.<ImMessageDO>lambdaQuery()
            .eq(ImMessageDO::getConversationId, conversationId)
            .gt(afterId != null && afterId > 0, ImMessageDO::getId, afterId)
            .orderByAsc(ImMessageDO::getId)).stream().map(ImServiceImpl::toMsgResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImMessageResp send(Long conversationId, String content) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ImConversationDO conv = requireMembership(conversationId, userId);
        if (StrUtil.isBlank(content))
            throw new BusinessException("消息内容不能为空");
        String trimmed = StrUtil.trim(content);
        if (trimmed.length() > 2000)
            throw new BusinessException("消息不能超过 2000 个字符");

        ImMessageDO msg = new ImMessageDO();
        msg.setConversationId(conversationId);
        msg.setSenderId(userId);
        msg.setContent(trimmed);
        msg.setIsRead(0);
        msg.setCreateTime(LocalDateTime.now());
        messageMapper.insert(msg);

        // 更新会话摘要 + 未读计数
        Long peerId = Objects.equals(conv.getUserAId(), userId) ? conv.getUserBId() : conv.getUserAId();
        boolean iAmA = Objects.equals(conv.getUserAId(), userId);
        String lastMsg = StrUtil.maxLength(trimmed, 100);
        LocalDateTime now = LocalDateTime.now();
        conversationMapper.update(null, Wrappers.<ImConversationDO>update()
            .eq("id", conversationId)
            .set("last_message", lastMsg)
            .set("last_time", now)
            .setSql(iAmA ? "unread_b = unread_b + 1" : "unread_a = unread_a + 1"));

        // 站内信通知对方
        messageService.send(peerId, NotificationEventEnum.POST_COMMENTED, "IM", conversationId, "新消息", "你有一条新消息,点击查看");

        ImMessageResp resp = new ImMessageResp();
        resp.setId(msg.getId());
        resp.setSenderId(userId);
        resp.setContent(trimmed);
        resp.setCreateTime(now);
        return resp;
    }

    @Override
    public void markRead(Long conversationId) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ImConversationDO conv = conversationMapper.selectById(conversationId);
        if (conv == null)
            return;
        boolean iAmA = Objects.equals(conv.getUserAId(), userId);
        int unread = iAmA ? conv.getUnreadA() : conv.getUnreadB();
        if (unread <= 0)
            return;
        // 将对方发给我的未读消息全部标已读
        Long peerId = iAmA ? conv.getUserBId() : conv.getUserAId();
        messageMapper.update(null, Wrappers.<ImMessageDO>update()
            .eq("conversation_id", conversationId)
            .eq("sender_id", peerId)
            .eq("is_read", 0)
            .set("is_read", 1));
        conversationMapper.update(null, Wrappers.<ImConversationDO>update()
            .eq("id", conversationId)
            .set(iAmA ? "unread_a" : "unread_b", 0));
    }

    @Override
    public void delete(Long conversationId) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ImConversationDO conv = requireMembership(conversationId, userId);
        // 仅自己视角清零未读(不做物理删除,保留对方视角)
        boolean iAmA = Objects.equals(conv.getUserAId(), userId);
        conversationMapper.update(null, Wrappers.<ImConversationDO>update()
            .eq("id", conversationId)
            .set(iAmA ? "unread_a" : "unread_b", 0)
            .set("last_message", ""));
    }

    private ImConversationDO requireMembership(Long conversationId, Long userId) {
        ImConversationDO conv = conversationMapper.selectById(conversationId);
        if (conv == null || (!Objects.equals(conv.getUserAId(), userId) && !Objects.equals(conv
            .getUserBId(), userId))) {
            throw new AppBizException(AppBizException.NOT_FOUND, "会话不存在");
        }
        return conv;
    }

    private ImConversationResp toResp(ImConversationDO conv, Long userId) {
        Long peerId = Objects.equals(conv.getUserAId(), userId) ? conv.getUserBId() : conv.getUserAId();
        int unread = Objects.equals(conv.getUserAId(), userId) ? conv.getUnreadA() : conv.getUnreadB();
        ImConversationResp resp = new ImConversationResp();
        resp.setId(conv.getId());
        resp.setPeerUserId(peerId);
        resp.setLastMessage(conv.getLastMessage());
        resp.setLastTime(conv.getLastTime());
        resp.setUnreadCount(unread);
        AppUserDO peer = appUserMapper.selectById(peerId);
        if (peer != null) {
            resp.setPeerNickname(peer.getNickname());
            resp.setPeerAvatar(peer.getAvatar());
        }
        return resp;
    }

    private static ImMessageResp toMsgResp(ImMessageDO msg) {
        ImMessageResp resp = new ImMessageResp();
        resp.setId(msg.getId());
        resp.setSenderId(msg.getSenderId());
        resp.setContent(msg.getContent());
        resp.setCreateTime(msg.getCreateTime());
        return resp;
    }
}
