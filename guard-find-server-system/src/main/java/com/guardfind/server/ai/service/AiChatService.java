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

package com.guardfind.server.ai.service;

import com.guardfind.server.ai.model.req.AiSendReq;
import com.guardfind.server.ai.model.resp.AiConversationResp;
import com.guardfind.server.ai.model.resp.AiMessageResp;
import com.guardfind.server.ai.model.resp.AiReplyResp;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.util.List;

/**
 * AI 对话业务接口(C 端)
 *
 * @author guardfind
 * @since 2026/10/28
 */
public interface AiChatService {

    /**
     * 新建会话
     *
     * @param title 标题(可空,默认"新对话")
     */
    AiConversationResp create(String title);

    /**
     * 当前用户会话分页(按最近活跃倒序)
     */
    PageResp<AiConversationResp> page(PageQuery pageQuery);

    /**
     * 会话消息列表(按时间升序;校验归属)
     */
    List<AiMessageResp> messages(Long conversationId);

    /**
     * 发送消息并取回助手回复(非流式)
     */
    AiReplyResp send(Long conversationId, AiSendReq req);

    /**
     * 删除会话(校验归属)
     */
    void delete(Long conversationId);
}
