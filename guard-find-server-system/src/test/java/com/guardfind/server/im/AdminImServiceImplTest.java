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

package com.guardfind.server.im;

import cn.dev33.satoken.stp.StpUtil;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.im.mapper.AdminMessageMapper;
import com.guardfind.server.im.model.entity.AdminMessageDO;
import com.guardfind.server.im.model.resp.AdminConversationResp;
import com.guardfind.server.im.service.impl.AdminImServiceImpl;
import com.guardfind.server.im.websocket.ImWebSocketHandler;
import com.guardfind.server.support.ContainerlessTestBase;
import com.guardfind.server.system.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectProvider;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 后台↔用户私信单测:会话聚合、未读统计、发送校验与 WS 推送。
 */
@DisplayName("后台私信")
class AdminImServiceImplTest extends ContainerlessTestBase {

    private AdminMessageMapper adminMessageMapper;
    private AppUserMapper appUserMapper;
    @SuppressWarnings("unchecked")
    private final ObjectProvider<UserService> userServiceProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ImWebSocketHandler> wsHandlerProvider = mock(ObjectProvider.class);
    private AdminImServiceImpl service;

    @BeforeEach
    void setUp() {
        adminMessageMapper = mock(AdminMessageMapper.class);
        appUserMapper = mock(AppUserMapper.class);
        service = new AdminImServiceImpl(adminMessageMapper, appUserMapper, userServiceProvider, wsHandlerProvider);
    }

    private AdminMessageDO msg(long id, long userId, String senderType, String content, int isRead) {
        AdminMessageDO m = new AdminMessageDO();
        m.setId(id);
        m.setUserId(userId);
        m.setSenderType(senderType);
        m.setAdminName("ADMIN".equals(senderType) ? "客服小助手" : null);
        m.setContent(content);
        m.setIsRead(isRead);
        m.setCreateTime(LocalDateTime.of(2026, 11, 2, 10, 0));
        return m;
    }

    @Test
    @DisplayName("会话按用户聚合:最近消息倒序 + 管理员未读数")
    void conversationsAggregatesByUser() {
        // 生产查询按 id 倒序返回,这里同样构造倒序输入
        when(adminMessageMapper.selectList(any())).thenReturn(List
            .of(msg(4, 200, "USER", "有人吗", 0), msg(3, 200, "USER", "在吗", 0), msg(2, 100, "ADMIN", "您好", 1), msg(1, 100, "USER", "你好", 0)));
        AppUserDO user100 = new AppUserDO();
        user100.setId(100L);
        user100.setNickname("张三");
        AppUserDO user200 = new AppUserDO();
        user200.setId(200L);
        user200.setNickname("李四");
        when(appUserMapper.selectByIds(any())).thenReturn(List.of(user100, user200));

        PageResp<AdminConversationResp> resp = service.conversations(new PageQuery());

        assertEquals(2, resp.getTotal());
        assertEquals(2, resp.getList().size());
        // 最近消息倒序:200 的最新消息 id=4 > 100 的 id=2
        assertEquals(200L, resp.getList().get(0).getUserId());
        assertEquals("有人吗", resp.getList().get(0).getLastMessage());
        // 用户发来且未读:200 有 2 条,100 有 1 条
        assertEquals(2, resp.getList().get(0).getUnreadCount());
        assertEquals(1, resp.getList().get(1).getUnreadCount());
        assertEquals("李四", resp.getList().get(0).getNickname());
    }

    @Test
    @DisplayName("拉取私信即把用户发来的未读标已读")
    void messagesMarksUserUnread() {
        when(adminMessageMapper.selectList(any())).thenReturn(List.of(msg(1, 100, "USER", "你好", 0)));

        service.messages(100L, null);

        verify(adminMessageMapper).update(any(), any());
    }

    @Test
    @DisplayName("发送校验:空白内容/超长内容拒绝,且不落库不推送")
    void sendRejectsInvalidContent() {
        BusinessException blank = assertThrows(BusinessException.class, () -> service.send(100L, "   "));
        assertTrue(blank.getMessage().contains("不能为空"));
        BusinessException tooLong = assertThrows(BusinessException.class, () -> service.send(100L, "超".repeat(2001)));
        assertTrue(tooLong.getMessage().contains("2000"));

        verify(adminMessageMapper, never()).insert(any(AdminMessageDO.class));
        verify(wsHandlerProvider, never()).getIfAvailable();
    }

    @Test
    @DisplayName("发送给不存在的用户拒绝")
    void sendRejectsUnknownUser() {
        when(appUserMapper.selectById(100L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.send(100L, "你好"));

        verify(adminMessageMapper, never()).insert(any(AdminMessageDO.class));
    }

    @Test
    @DisplayName("发送成功:落库 ADMIN 类型 + 通过 WS 推送用户")
    void sendInsertsAndPushes() {
        AppUserDO user = new AppUserDO();
        user.setId(100L);
        user.setNickname("张三");
        when(appUserMapper.selectById(100L)).thenReturn(user);
        ImWebSocketHandler handler = mock(ImWebSocketHandler.class);
        when(wsHandlerProvider.getIfAvailable()).thenReturn(handler);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(9L);

            service.send(100L, "  你好,我是官方客服  ");

            ArgumentCaptor<AdminMessageDO> captor = ArgumentCaptor.forClass(AdminMessageDO.class);
            verify(adminMessageMapper).insert(captor.capture());
            assertEquals("ADMIN", captor.getValue().getSenderType());
            assertEquals("你好,我是官方客服", captor.getValue().getContent());
            assertEquals(0, captor.getValue().getIsRead());
            assertEquals(9L, captor.getValue().getAdminId());
            verify(handler).pushAdminMessage(100L, captor.getValue().getId(), "你好,我是官方客服", captor.getValue()
                .getAdminName());
        }
    }
}
