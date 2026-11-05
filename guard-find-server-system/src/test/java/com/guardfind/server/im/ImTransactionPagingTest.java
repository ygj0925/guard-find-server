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

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.im.mapper.AdminMessageMapper;
import com.guardfind.server.im.mapper.ImConversationMapper;
import com.guardfind.server.im.mapper.ImMessageMapper;
import com.guardfind.server.im.model.entity.AdminMessageDO;
import com.guardfind.server.im.model.entity.ImConversationDO;
import com.guardfind.server.im.model.entity.ImMessageDO;
import com.guardfind.server.im.service.impl.ImServiceImpl;
import com.guardfind.server.im.websocket.ImWebSocketHandler;
import com.guardfind.server.notification.service.AppMessageService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImTransactionPagingTest {

    private final ImConversationMapper conversations = mock(ImConversationMapper.class);
    private final ImMessageMapper messages = mock(ImMessageMapper.class);
    private final AdminMessageMapper adminMessages = mock(AdminMessageMapper.class);
    private final AppMessageService notices = mock(AppMessageService.class);
    private final ImWebSocketHandler ws = mock(ImWebSocketHandler.class);
    private final ObjectProvider<ImWebSocketHandler> wsProvider = provider();
    private final ImServiceImpl service = new ImServiceImpl(conversations, messages,
        mock(AppUserMapper.class), notices, adminMessages, wsProvider);

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ImWebSocketHandler> provider() {
        ObjectProvider<ImWebSocketHandler> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mock(ImWebSocketHandler.class));
        return provider;
    }

    @BeforeEach
    void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, ImConversationDO.class);
        TableInfoHelper.initTableInfo(assistant, ImMessageDO.class);
        TableInfoHelper.initTableInfo(assistant, AdminMessageDO.class);
    }

    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private ImConversationDO conversation() {
        ImConversationDO conv = new ImConversationDO();
        conv.setId(10L);
        conv.setUserAId(1L);
        conv.setUserBId(2L);
        conv.setUnreadA(3);
        conv.setUnreadB(0);
        return conv;
    }

    private ImMessageDO message(long id, long senderId, int read) {
        ImMessageDO msg = new ImMessageDO();
        msg.setId(id);
        msg.setConversationId(10L);
        msg.setSenderId(senderId);
        msg.setContent("m" + id);
        msg.setIsRead(read);
        return msg;
    }

    @Test
    void sendsRealtimePushOnlyAfterCommit() {
        when(conversations.selectOne(any())).thenReturn(conversation());
        when(wsProvider.getIfAvailable()).thenReturn(ws);
        Mockito.doAnswer(invocation -> {
            ((ImMessageDO) invocation.getArgument(0)).setId(99L);
            return 1;
        }).when(messages).insert(any(ImMessageDO.class));

        try (MockedStatic<AppUserContextHolder> context = Mockito.mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(1L);
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(true);

            service.send(10L, "hello");

            verify(ws, never()).pushNewMessage(any(), any(), any(), any(), any());
            TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
            verify(ws).pushNewMessage(2L, 10L, 1L, "hello", 99L);
        }
    }

    @Test
    void rolledBackTransactionNeverPushes() {
        when(conversations.selectOne(any())).thenReturn(conversation());
        when(wsProvider.getIfAvailable()).thenReturn(ws);
        Mockito.doAnswer(invocation -> {
            ((ImMessageDO) invocation.getArgument(0)).setId(99L);
            return 1;
        }).when(messages).insert(any(ImMessageDO.class));

        try (MockedStatic<AppUserContextHolder> context = Mockito.mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(1L);
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(true);

            service.send(10L, "hello");
            TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            verify(ws, never()).pushNewMessage(any(), any(), any(), any(), any());
        }
    }

    @Test
    void messagePageIsBoundedAndMarksOnlyReturnedPeerMessages() {
        when(conversations.selectById(10L)).thenReturn(conversation());
        when(messages.selectList(any())).thenReturn(List.of(message(101L, 2L, 0), message(102L, 1L, 0)));
        when(messages.update(isNull(), any())).thenReturn(1);

        try (MockedStatic<AppUserContextHolder> context = Mockito.mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(1L);

            assertEquals(2, service.messages(10L, null).size());
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<ImMessageDO>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(messages).selectList(query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("LIMIT 100"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<ImMessageDO>> update = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(messages).update(isNull(), update.capture());
        assertTrue(update.getValue().getSqlSegment().contains("sender_id"));
        assertTrue(update.getValue().getParamNameValuePairs().values().contains(2L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateWrapper<ImConversationDO>> decrement = ArgumentCaptor
            .forClass(UpdateWrapper.class);
        verify(conversations).update(isNull(), decrement.capture());
        assertTrue(decrement.getValue().getSqlSet().contains("unread_a=GREATEST(0,unread_a-1)"));
    }

    @Test
    void supportPageIsBoundedAndAcknowledgesOnlyReturnedAdminMessages() {
        AdminMessageDO reply = new AdminMessageDO();
        reply.setId(5L);
        reply.setUserId(1L);
        reply.setSenderType("ADMIN");
        reply.setContent("reply");
        reply.setIsRead(1);
        when(adminMessages.selectList(any())).thenReturn(List.of(reply));

        try (MockedStatic<AppUserContextHolder> context = Mockito.mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(1L);

            assertEquals(1, service.supportMessages(null).size());
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<AdminMessageDO>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(adminMessages).selectList(query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("LIMIT 100"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateWrapper<AdminMessageDO>> update = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(adminMessages).update(isNull(), update.capture());
        assertTrue(update.getValue().getSqlSegment().contains("id IN"));
    }
}
