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

package com.guardfind.server.ai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;
import top.continew.starter.ratelimiter.annotation.RateLimiter;
import top.continew.starter.ratelimiter.enums.LimitType;

import com.guardfind.server.ai.model.req.AiSendReq;
import com.guardfind.server.ai.model.resp.AiConversationResp;
import com.guardfind.server.ai.model.resp.AiMessageResp;
import com.guardfind.server.ai.model.resp.AiReplyResp;
import com.guardfind.server.ai.service.AiChatService;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * AI 对话 API(C 端)
 *
 * <p>
 * /app/** 已在 sa-token 配置中放行后台鉴权,登录态由 {@code AppUserContextHolder} 判定。
 * Key 只在服务端配置;未配置时接口正常返回,但内容是明确标注的测试模式回复。
 * 一期非流式(整包返回);流式待 RN/鸿蒙侧 SSE 适配验证后放开。
 * </p>
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Tag(name = "AI 对话 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/ai")
public class AiChatController {

    private final AiChatService aiChatService;

    @Operation(summary = "新建会话", description = "须登录;标题可空,默认「新对话」,首条消息后自动回填摘要")
    @PostMapping("/conversation")
    public AiConversationResp create(@RequestParam(required = false) String title) {
        return aiChatService.create(title);
    }

    @Operation(summary = "会话分页", description = "按最近活跃倒序")
    @GetMapping("/conversation/page")
    public PageResp<AiConversationResp> page(PageQuery pageQuery) {
        return aiChatService.page(pageQuery);
    }

    @Operation(summary = "会话消息列表", description = "按时间升序;仅本人会话可查")
    @Parameter(name = "id", description = "会话 ID", required = true)
    @GetMapping("/conversation/{id}/message")
    public List<AiMessageResp> messages(@PathVariable Long id) {
        return aiChatService.messages(id);
    }

    @Operation(summary = "发送消息", description = "返回助手回复;携带最近 20 条历史作为上下文;未配置 Key 时为测试模式回复")
    @Parameter(name = "id", description = "会话 ID", required = true)
    @RateLimiter(name = "APP:AI:SEND", rate = 30, interval = 1, unit = TimeUnit.MINUTES, type = LimitType.IP, message = "发送太频繁,请稍后再试")
    @PostMapping("/conversation/{id}/message")
    public AiReplyResp send(@PathVariable Long id, @RequestBody @Valid AiSendReq req) {
        return aiChatService.send(id, req);
    }

    @Operation(summary = "删除会话", description = "仅本人会话可删,消息级联删除")
    @Parameter(name = "id", description = "会话 ID", required = true)
    @DeleteMapping("/conversation/{id}")
    public void delete(@PathVariable Long id) {
        aiChatService.delete(id);
    }
}
