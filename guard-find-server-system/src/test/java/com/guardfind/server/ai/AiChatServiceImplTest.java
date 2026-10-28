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

package com.guardfind.server.ai;

import com.guardfind.server.ai.config.AiChatProperties;
import com.guardfind.server.ai.mapper.AiConversationMapper;
import com.guardfind.server.ai.mapper.AiMessageMapper;
import com.guardfind.server.ai.model.entity.AiConversationDO;
import com.guardfind.server.ai.model.entity.AiMessageDO;
import com.guardfind.server.ai.model.req.AiSendReq;
import com.guardfind.server.ai.model.resp.AiReplyResp;
import com.guardfind.server.ai.service.impl.AiChatServiceImpl;
import com.guardfind.server.ai.support.LlmClient;
import com.guardfind.server.ai.support.LlmMessage;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.exception.AppBizException;
import com.guardfind.server.support.ContainerlessTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.continew.starter.core.exception.BusinessException;

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
 * {@link AiChatServiceImpl} 单测:归属校验、消息落库顺序、标题回填、无 Key 测试模式、上下文组装。
 *
 * @author guardfind
 * @since 2026/10/28
 */
class AiChatServiceImplTest extends ContainerlessTestBase {

    private AiConversationMapper conversationMapper;
    private AiMessageMapper messageMapper;
    private LlmClient llmClient;
    private AiChatProperties properties;
    private AiChatServiceImpl service;

    @BeforeEach
    void setUp() {
        conversationMapper = mock(AiConversationMapper.class);
        messageMapper = mock(AiMessageMapper.class);
        llmClient = mock(LlmClient.class);
        properties = new AiChatProperties();
        properties.setModel("glm-4-flash");
        service = new AiChatServiceImpl(conversationMapper, messageMapper, llmClient, properties);
    }

    private AiSendReq sendReq(String content) {
        AiSendReq req = new AiSendReq();
        req.setContent(content);
        return req;
    }

    private AiConversationDO conversation(Long id, Long userId, String title) {
        AiConversationDO conversation = new AiConversationDO();
        conversation.setId(id);
        conversation.setUserId(userId);
        conversation.setTitle(title);
        conversation.setModel("glm-4-flash");
        return conversation;
    }

    private void stubInsertIds() {
        when(messageMapper.insert(any(AiMessageDO.class))).thenAnswer(invocation -> {
            AiMessageDO message = invocation.getArgument(0);
            message.setId(message.getId() == null ? (long)message.hashCode() : message.getId());
            return 1;
        });
    }

    @Test
    @DisplayName("发送给他人会话返回 NOT_FOUND,不调用 LLM 不落库")
    void sendRejectsForeignConversation() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(conversationMapper.selectById(5L)).thenReturn(conversation(5L, 8L, "别人的会话"));
            AppBizException ex = assertThrows(AppBizException.class, () -> service.send(5L, sendReq("hi")));
            assertEquals(AppBizException.NOT_FOUND, ex.getCode());
            verify(messageMapper, never()).insert(any(AiMessageDO.class));
            verify(llmClient, never()).chat(any());
        }
    }

    @Test
    @DisplayName("无 Key 测试模式:不调 LLM,用户与助手消息都落库,标题回填摘要")
    void sendWithoutKeyUsesMockReply() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(conversationMapper.selectById(5L)).thenReturn(conversation(5L, 7L, "新对话"));
            stubInsertIds();

            AiReplyResp resp = service.send(5L, sendReq("今天天气怎么样?"));

            assertTrue(resp.getMockMode());
            assertEquals("assistant", resp.getReply().getRole());
            assertTrue(resp.getReply().getContent().contains("测试模式"));
            assertTrue(resp.getReply().getContent().contains("今天天气怎么样?"));
            assertTrue(resp.getTitle().contains("今天天气怎么样"));
            verify(llmClient, never()).chat(any());
            ArgumentCaptor<AiMessageDO> captor = ArgumentCaptor.forClass(AiMessageDO.class);
            verify(messageMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
            assertEquals("user", captor.getAllValues().get(0).getRole());
            assertEquals("assistant", captor.getAllValues().get(1).getRole());
        }
    }

    @Test
    @DisplayName("有 Key:上下文 = system 提示词 + 历史升序 + 本条消息;用户自定义标题不覆盖")
    void sendWithKeyAssemblesContext() {
        properties.setApiKey("test-key");
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            AiConversationDO conversation = conversation(5L, 7L, "自定义标题");
            when(conversationMapper.selectById(5L)).thenReturn(conversation);
            when(messageMapper.insert(any(AiMessageDO.class))).thenAnswer(invocation -> {
                AiMessageDO message = invocation.getArgument(0);
                message.setId("user".equals(message.getRole()) ? 100L : 200L);
                return 1;
            });
            // 历史:1 条旧的用户消息(id=50),倒序返回模拟 orderByDesc(id)
            AiMessageDO historyMessage = new AiMessageDO();
            historyMessage.setId(50L);
            historyMessage.setConversationId(5L);
            historyMessage.setRole("user");
            historyMessage.setContent("上一句");
            when(messageMapper.selectList(any())).thenReturn(List.of(historyMessage));
            when(llmClient.chat(any())).thenReturn("这是回复");

            AiReplyResp resp = service.send(5L, sendReq("这一句"));

            assertEquals("这是回复", resp.getReply().getContent());
            assertEquals(Boolean.FALSE, resp.getMockMode());
            @SuppressWarnings({"unchecked",
                "rawtypes"}) ArgumentCaptor<List<LlmMessage>> captor = (ArgumentCaptor)ArgumentCaptor
                    .forClass(List.class);
            verify(llmClient).chat(captor.capture());
            List<LlmMessage> context = captor.getValue();
            assertEquals("system", context.get(0).role());
            assertEquals("上一句", context.get(1).content());
            assertEquals("这一句", context.get(context.size() - 1).content());
            // 自定义标题不被覆盖
            assertEquals("自定义标题", resp.getTitle());
        }
    }

    @Test
    @DisplayName("LLM 返回空内容按业务失败处理,提示换问法")
    void sendRejectsEmptyReply() {
        properties.setApiKey("test-key");
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(conversationMapper.selectById(5L)).thenReturn(conversation(5L, 7L, "新对话"));
            when(messageMapper.insert(any(AiMessageDO.class))).thenAnswer(invocation -> {
                AiMessageDO message = invocation.getArgument(0);
                message.setId(100L);
                return 1;
            });
            when(messageMapper.selectList(any())).thenReturn(List.of());
            when(llmClient.chat(any())).thenReturn("  ");
            assertThrows(BusinessException.class, () -> service.send(5L, sendReq("hi")));
        }
    }

    @Test
    @DisplayName("删除会话级联删除消息")
    void deleteCascadesMessages() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(conversationMapper.selectById(5L)).thenReturn(conversation(5L, 7L, "我的会话"));
            service.delete(5L);
            verify(conversationMapper).deleteById(5L);
            verify(messageMapper).delete(any());
        }
    }
}
