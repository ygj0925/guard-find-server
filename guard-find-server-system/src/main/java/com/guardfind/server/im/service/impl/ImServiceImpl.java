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
import com.guardfind.server.im.mapper.AdminMessageMapper;
import com.guardfind.server.im.mapper.ImConversationMapper;
import com.guardfind.server.im.mapper.ImMessageMapper;
import com.guardfind.server.im.model.entity.AdminMessageDO;
import com.guardfind.server.im.model.entity.ImConversationDO;
import com.guardfind.server.im.model.entity.ImMessageDO;
import com.guardfind.server.im.model.resp.ImConversationResp;
import com.guardfind.server.im.model.resp.ImMessageResp;
import com.guardfind.server.im.service.ImService;
import com.guardfind.server.im.support.AfterCommit;
import com.guardfind.server.im.websocket.ImWebSocketHandler;
import com.guardfind.server.notification.enums.NotificationEventEnum;
import com.guardfind.server.notification.service.AppMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
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
 * IM 私信实现(REST 落库 + WebSocket 实时推送,断线自动降级轮询)。
 * 会话键: user_a_id < user_b_id 保证唯一对;unread_a/b 分别计未读;
 * 发消息时对方收到站内信通知 + WS 实时推送;删除会话仅自己视角隐藏。
 */
@Service
@RequiredArgsConstructor
public class ImServiceImpl implements ImService {

    private final ImConversationMapper conversationMapper;
    private final ImMessageMapper messageMapper;
    private final AppUserMapper appUserMapper;
    private final AppMessageService messageService;
    private final AdminMessageMapper adminMessageMapper;
    /** 延迟获取 WS Handler,避免与 ImService 的循环依赖 */
    private final ObjectProvider<ImWebSocketHandler> wsHandlerProvider;

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
        ImConversationDO conv = requireMembership(conversationId, userId);
        List<ImMessageDO> msgs = messageMapper.selectList(Wrappers.<ImMessageDO>lambdaQuery()
            .eq(ImMessageDO::getConversationId, conversationId)
            .gt(afterId != null && afterId > 0, ImMessageDO::getId, afterId)
            .orderByAsc(ImMessageDO::getId)
            .last("LIMIT 100"));
        // 只把本次真正返回给客户端的对方消息标已读,未翻到的历史仍保持未读。
        if (!msgs.isEmpty()) {
            boolean iAmA = Objects.equals(conv.getUserAId(), userId);
            Long peerId = iAmA ? conv.getUserBId() : conv.getUserAId();
            int read = messageMapper.update(null, Wrappers.<ImMessageDO>lambdaUpdate()
                .in(ImMessageDO::getId, msgs.stream().map(ImMessageDO::getId).toList())
                .eq(ImMessageDO::getSenderId, peerId)
                .eq(ImMessageDO::getIsRead, 0)
                .set(ImMessageDO::getIsRead, 1));
            if (read > 0) {
                String column = iAmA ? "unread_a" : "unread_b";
                conversationMapper.update(null, Wrappers.<ImConversationDO>update()
                    .eq("id", conversationId)
                    .setSql(column + "=GREATEST(0," + column + "-" + read + ")"));
            }
        }
        return msgs.stream().map(ImServiceImpl::toMsgResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImMessageResp send(Long conversationId, String content) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        // 锁住会话行,同一会话的发送串行分配未读与摘要,避免并发覆盖。
        ImConversationDO conv = requireMembershipForUpdate(conversationId, userId);
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

        // WebSocket 实时推送(对方不在线时无副作用,客户端降级轮询);事务提交后再发,回滚不推送。
        ImWebSocketHandler wsHandler = wsHandlerProvider.getIfAvailable();
        if (wsHandler != null) {
            AfterCommit.run(() -> wsHandler.pushNewMessage(peerId, conversationId, userId, trimmed, msg.getId()));
        }

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
        ImConversationDO conv = requireMembership(conversationId, userId);
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
            .set(iAmA ? "unread_a" : "unread_b", 0));
    }

    @Override
    public List<ImMessageResp> supportMessages(Long afterId) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        List<AdminMessageDO> msgs = adminMessageMapper.selectList(Wrappers.<AdminMessageDO>lambdaQuery()
            .eq(AdminMessageDO::getUserId, userId)
            .gt(afterId != null && afterId > 0, AdminMessageDO::getId, afterId)
            .orderByAsc(AdminMessageDO::getId)
            .last("LIMIT 100"));
        // 只把本次返回的客服回复标已读,避免未返回的告警消息被提前吞掉。
        if (!msgs.isEmpty()) {
            adminMessageMapper.update(null, Wrappers.<AdminMessageDO>update()
                .in("id", msgs.stream().map(AdminMessageDO::getId).toList())
                .eq("user_id", userId)
                .eq("sender_type", "ADMIN")
                .eq("is_read", 0)
                .set("is_read", 1));
        }
        return msgs.stream().map(m -> {
            ImMessageResp resp = new ImMessageResp();
            resp.setId(m.getId());
            // 客服消息 senderId 固定 0,App 端据此前后区分气泡
            resp.setSenderId("USER".equals(m.getSenderType()) ? userId : 0L);
            resp.setContent(m.getContent());
            resp.setCreateTime(m.getCreateTime());
            return resp;
        }).toList();
    }

    @Override
    public ImMessageResp sendSupport(String content) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        if (StrUtil.isBlank(content)) {
            throw new BusinessException("消息内容不能为空");
        }
        String trimmed = StrUtil.trim(content);
        if (trimmed.length() > 2000) {
            throw new BusinessException("消息不能超过 2000 个字符");
        }
        AdminMessageDO msg = new AdminMessageDO();
        msg.setUserId(userId);
        msg.setSenderType("USER");
        msg.setContent(trimmed);
        msg.setIsRead(0);
        msg.setCreateTime(LocalDateTime.now());
        adminMessageMapper.insert(msg);
        ImMessageResp resp = new ImMessageResp();
        resp.setId(msg.getId());
        resp.setSenderId(userId);
        resp.setContent(trimmed);
        resp.setCreateTime(msg.getCreateTime());
        return resp;
    }

    private ImConversationDO requireMembership(Long conversationId, Long userId) {
        ImConversationDO conv = conversationMapper.selectById(conversationId);
        if (conv == null || (!Objects.equals(conv.getUserAId(), userId) && !Objects.equals(conv
            .getUserBId(), userId))) {
            throw new AppBizException(AppBizException.NOT_FOUND, "会话不存在");
        }
        return conv;
    }

    private ImConversationDO requireMembershipForUpdate(Long conversationId, Long userId) {
        ImConversationDO conv = conversationMapper.selectOne(Wrappers.<ImConversationDO>lambdaQuery()
            .eq(ImConversationDO::getId, conversationId)
            .last("FOR UPDATE"));
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
