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

package com.guardfind.server.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.guardfind.server.ai.config.AiChatProperties;
import com.guardfind.server.ai.mapper.AiConversationMapper;
import com.guardfind.server.ai.mapper.AiMessageMapper;
import com.guardfind.server.ai.model.entity.AiConversationDO;
import com.guardfind.server.ai.model.entity.AiMessageDO;
import com.guardfind.server.ai.model.req.AiSendReq;
import com.guardfind.server.ai.model.resp.AiConversationResp;
import com.guardfind.server.ai.model.resp.AiMessageResp;
import com.guardfind.server.ai.model.resp.AiReplyResp;
import com.guardfind.server.ai.service.AiChatService;
import com.guardfind.server.ai.support.LlmClient;
import com.guardfind.server.ai.support.LlmMessage;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.exception.AppBizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 对话实现
 *
 * <p>
 * 发送消息的事务边界:用户消息与助手消息一起落库,LLM 调用放在事务内
 * (单机同步调用,失败即整体不落库,客户端重发即可;流式版需改为先落用户消息)。
 * 未配置 apiKey 时进入测试模式:返回明确标注的兜底回复,不调供应商。
 * </p>
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private static final String DEFAULT_TITLE = "新对话";
    private static final String SYSTEM_PROMPT = "你是「工具箱」App 里的 AI 助手,回答简洁、实用、友好," + "默认使用中文;用户明确要求其他语言时跟随用户。不讨论违法内容。";
    private static final String MOCK_REPLY = "【测试模式】服务端还没有配置 AI 供应商的 Key," + "目前返回的是占位回复。收到你的消息:%s";

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final LlmClient llmClient;
    private final AiChatProperties properties;

    @Override
    public AiConversationResp create(String title) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        AiConversationDO conversation = new AiConversationDO();
        conversation.setUserId(userId);
        conversation.setTitle(StrUtil.blankToDefault(StrUtil.trim(title), DEFAULT_TITLE));
        conversation.setModel(properties.getModel());
        conversationMapper.insert(conversation);
        return toConversationResp(conversation);
    }

    @Override
    public PageResp<AiConversationResp> page(PageQuery pageQuery) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        Page<AiConversationDO> page = conversationMapper
            .selectPage(new Page<>(pageNum(pageQuery), pageSize(pageQuery)), Wrappers.<AiConversationDO>lambdaQuery()
                .eq(AiConversationDO::getUserId, userId)
                .orderByDesc(AiConversationDO::getUpdateTime)
                .orderByDesc(AiConversationDO::getId));
        List<AiConversationResp> list = page.getRecords().stream().map(this::toConversationResp).toList();
        PageResp<AiConversationResp> resp = new PageResp<>();
        resp.setList(list);
        resp.setTotal(page.getTotal());
        return resp;
    }

    @Override
    public List<AiMessageResp> messages(Long conversationId) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        getOwnedConversation(conversationId, userId);
        return messageMapper.selectList(Wrappers.<AiMessageDO>lambdaQuery()
            .eq(AiMessageDO::getConversationId, conversationId)
            .orderByAsc(AiMessageDO::getId)).stream().map(AiChatServiceImpl::toMessageResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiReplyResp send(Long conversationId, AiSendReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        AiConversationDO conversation = getOwnedConversation(conversationId, userId);
        String content = StrUtil.trim(req.getContent());

        AiMessageDO userMessage = insertMessage(conversationId, userId, "user", content);

        // 组装上下文:system 提示词 + 最近 N 条历史 + 本条消息(按 id 升序裁剪)
        List<LlmMessage> context = new ArrayList<>();
        context.add(LlmMessage.system(SYSTEM_PROMPT));
        int turns = Math.max(0, properties.getHistoryTurns());
        List<AiMessageDO> history = messageMapper.selectList(Wrappers.<AiMessageDO>lambdaQuery()
            .eq(AiMessageDO::getConversationId, conversationId)
            .ne(AiMessageDO::getId, userMessage.getId())
            .orderByDesc(AiMessageDO::getId)
            .last("LIMIT " + turns * 2));
        for (int i = history.size() - 1; i >= 0; i--) {
            AiMessageDO item = history.get(i);
            context.add("user".equals(item.getRole())
                ? LlmMessage.user(item.getContent())
                : LlmMessage.assistant(item.getContent()));
        }
        context.add(LlmMessage.user(content));

        boolean mockMode = StrUtil.isBlank(properties.getApiKey());
        String replyText = mockMode
            ? String.format(MOCK_REPLY, StrUtil.maxLength(content, 100))
            : llmClient.chat(context);
        if (StrUtil.isBlank(replyText)) {
            throw new BusinessException("AI 没有返回内容,请换个问法试试");
        }
        AiMessageDO reply = insertMessage(conversationId, userId, "assistant", replyText);

        // 首条消息回填会话标题(默认标题才覆盖,不抢用户自定义标题)
        String title = conversation.getTitle();
        if (DEFAULT_TITLE.equals(title)) {
            title = StrUtil.maxLength(StrUtil.cleanBlank(content), 20);
            conversation.setTitle(title);
        }
        conversation.setModel(properties.getModel());
        conversationMapper.updateById(conversation);

        AiReplyResp resp = new AiReplyResp();
        resp.setConversationId(conversationId);
        resp.setTitle(title);
        resp.setReply(toMessageResp(reply));
        resp.setMockMode(mockMode);
        return resp;
    }

    @Override
    public void delete(Long conversationId) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        AiConversationDO conversation = getOwnedConversation(conversationId, userId);
        conversationMapper.deleteById(conversation.getId());
        messageMapper.delete(Wrappers.<AiMessageDO>lambdaQuery().eq(AiMessageDO::getConversationId, conversationId));
    }

    private AiMessageDO insertMessage(Long conversationId, Long userId, String role, String content) {
        AiMessageDO message = new AiMessageDO();
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        messageMapper.insert(message);
        return message;
    }

    private AiConversationDO getOwnedConversation(Long conversationId, Long userId) {
        if (conversationId == null) {
            throw new BusinessException("会话 ID 不能为空");
        }
        AiConversationDO conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            // 越权与不存在同一话术,与记账口径一致
            throw new AppBizException(AppBizException.NOT_FOUND, "会话不存在");
        }
        return conversation;
    }

    private AiConversationResp toConversationResp(AiConversationDO conversation) {
        AiConversationResp resp = new AiConversationResp();
        resp.setId(conversation.getId());
        resp.setTitle(conversation.getTitle());
        resp.setModel(conversation.getModel());
        resp.setUpdateTime(conversation.getUpdateTime());
        resp.setMockMode(StrUtil.isBlank(properties.getApiKey()));
        return resp;
    }

    private static AiMessageResp toMessageResp(AiMessageDO message) {
        AiMessageResp resp = new AiMessageResp();
        resp.setId(message.getId());
        resp.setRole(message.getRole());
        resp.setContent(message.getContent());
        resp.setCreateTime(message.getCreateTime());
        return resp;
    }

    private int pageNum(PageQuery pageQuery) {
        return pageQuery == null || pageQuery.getPage() == null ? 1 : pageQuery.getPage();
    }

    private int pageSize(PageQuery pageQuery) {
        return pageQuery == null || pageQuery.getSize() == null ? 20 : pageQuery.getSize();
    }
}
