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

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.im.mapper.AdminMessageMapper;
import com.guardfind.server.im.model.entity.AdminMessageDO;
import com.guardfind.server.im.model.resp.AdminConversationResp;
import com.guardfind.server.im.model.resp.AdminMessageResp;
import com.guardfind.server.im.service.AdminImService;
import com.guardfind.server.im.websocket.ImWebSocketHandler;
import com.guardfind.server.system.model.entity.user.UserDO;
import com.guardfind.server.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 后台↔用户私信实现。消息按 user_id 聚合为会话;
 * 管理员发消息时通过 ImWebSocketHandler 实时推给在线用户,离线用户由 App 端轮询兜底。
 */
@Service
@RequiredArgsConstructor
public class AdminImServiceImpl implements AdminImService {

    private static final String SENDER_ADMIN = "ADMIN";
    private static final String SENDER_USER = "USER";
    /** 会话聚合扫描上限:后台私信量级下足够,超过时只影响更早会话的展示 */
    private static final int CONVERSATION_SCAN_LIMIT = 5000;

    private final AdminMessageMapper adminMessageMapper;
    private final AppUserMapper appUserMapper;
    private final ObjectProvider<UserService> userServiceProvider;
    private final ObjectProvider<ImWebSocketHandler> wsHandlerProvider;

    @Override
    public PageResp<AdminConversationResp> conversations(PageQuery pageQuery) {
        List<AdminMessageDO> recent = adminMessageMapper.selectList(Wrappers.<AdminMessageDO>lambdaQuery()
            .orderByDesc(AdminMessageDO::getId)
            .last("LIMIT " + CONVERSATION_SCAN_LIMIT));
        // 按 user_id 聚合:保留最近一条 + 统计管理员未读
        Map<Long, AdminMessageDO> lastByUser = new LinkedHashMap<>();
        Map<Long, Integer> unreadByUser = new LinkedHashMap<>();
        for (AdminMessageDO msg : recent) {
            lastByUser.putIfAbsent(msg.getUserId(), msg);
            if (SENDER_USER.equals(msg.getSenderType()) && msg.getIsRead() == 0) {
                unreadByUser.merge(msg.getUserId(), 1, Integer::sum);
            }
        }
        List<Long> orderedUserIds = lastByUser.keySet()
            .stream()
            .sorted(Comparator.comparing((Long uid) -> lastByUser.get(uid).getId()).reversed())
            .toList();
        int page = pageQuery == null || pageQuery.getPage() == null ? 1 : pageQuery.getPage();
        int size = pageQuery == null || pageQuery.getSize() == null ? 10 : pageQuery.getSize();
        List<Long> pageUserIds = orderedUserIds.stream().skip((long)(page - 1) * size).limit(size).toList();
        Map<Long, AppUserDO> users = pageUserIds.isEmpty()
            ? Map.of()
            : appUserMapper.selectByIds(pageUserIds).stream().collect(Collectors.toMap(AppUserDO::getId, u -> u));
        List<AdminConversationResp> list = pageUserIds.stream().map(userId -> {
            AdminMessageDO last = lastByUser.get(userId);
            AdminConversationResp resp = new AdminConversationResp();
            resp.setUserId(userId);
            resp.setLastMessage(last.getContent());
            resp.setLastTime(last.getCreateTime());
            resp.setUnreadCount(unreadByUser.getOrDefault(userId, 0));
            AppUserDO user = users.get(userId);
            if (user != null) {
                resp.setNickname(user.getNickname());
                resp.setAvatar(user.getAvatar());
            }
            return resp;
        }).toList();
        PageResp<AdminConversationResp> pageResp = new PageResp<>();
        pageResp.setList(list);
        pageResp.setTotal(orderedUserIds.size());
        return pageResp;
    }

    @Override
    public List<AdminMessageResp> messages(Long userId, Long afterId) {
        List<AdminMessageDO> msgs = adminMessageMapper.selectList(Wrappers.<AdminMessageDO>lambdaQuery()
            .eq(AdminMessageDO::getUserId, userId)
            .gt(afterId != null && afterId > 0, AdminMessageDO::getId, afterId)
            .orderByAsc(AdminMessageDO::getId));
        // 拉取即把用户发来的未读标已读
        adminMessageMapper.update(null, Wrappers.<AdminMessageDO>update()
            .eq("user_id", userId)
            .eq("sender_type", SENDER_USER)
            .eq("is_read", 0)
            .set("is_read", 1));
        return msgs.stream().map(AdminImServiceImpl::toResp).toList();
    }

    @Override
    public AdminMessageResp send(Long userId, String content) {
        if (StrUtil.isBlank(content)) {
            throw new BusinessException("消息内容不能为空");
        }
        String trimmed = StrUtil.trim(content);
        if (trimmed.length() > 2000) {
            throw new BusinessException("消息不能超过 2000 个字符");
        }
        AppUserDO user = appUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        AdminMessageDO msg = new AdminMessageDO();
        msg.setUserId(userId);
        msg.setSenderType(SENDER_ADMIN);
        msg.setAdminId(StpUtil.getLoginIdAsLong());
        msg.setAdminName(currentAdminName());
        msg.setContent(trimmed);
        msg.setIsRead(0);
        msg.setCreateTime(LocalDateTime.now());
        adminMessageMapper.insert(msg);

        // 用户在线时实时推送(与 C 端 IM 共用同一条 /ws/im 连接)
        ImWebSocketHandler wsHandler = wsHandlerProvider.getIfAvailable();
        if (wsHandler != null) {
            wsHandler.pushAdminMessage(userId, msg.getId(), trimmed, msg.getAdminName());
        }

        return toResp(msg);
    }

    private String currentAdminName() {
        UserService userService = userServiceProvider.getIfAvailable();
        if (userService != null) {
            UserDO admin = userService.getById(StpUtil.getLoginIdAsLong());
            if (admin != null && StrUtil.isNotBlank(admin.getNickname())) {
                return admin.getNickname();
            }
        }
        return "官方客服";
    }

    private static AdminMessageResp toResp(AdminMessageDO msg) {
        AdminMessageResp resp = new AdminMessageResp();
        resp.setId(msg.getId());
        resp.setSenderType(msg.getSenderType());
        resp.setAdminName(msg.getAdminName());
        resp.setContent(msg.getContent());
        resp.setCreateTime(msg.getCreateTime());
        return resp;
    }
}
