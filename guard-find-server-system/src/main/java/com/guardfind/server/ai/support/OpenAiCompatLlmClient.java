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

package com.guardfind.server.ai.support;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.guardfind.server.ai.config.AiChatProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.continew.starter.core.exception.BusinessException;

import java.util.List;

/**
 * OpenAI 兼容协议客户端(/chat/completions)
 *
 * <p>
 * 兼容 OpenAI / GLM / DeepSeek / 自建网关等所有「Authorization: Bearer + chat/completions」协议;
 * 非流式(一次回包),流式放在二期后半做(RN/鸿蒙侧 SSE 无已验证适配)。
 * 供应商错误统一转 BusinessException,msg 里去掉内部细节,原文进日志。
 * </p>
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCompatLlmClient implements LlmClient {

    private final AiChatProperties properties;

    @Override
    public String chat(List<LlmMessage> messages) {
        JSONObject body = new JSONObject();
        body.set("model", properties.getModel());
        JSONArray array = new JSONArray();
        messages.forEach(message -> array.add(new JSONObject().set("role", message.role())
            .set("content", message.content())));
        body.set("messages", array);
        body.set("stream", false);

        String url = StrUtil.removeSuffix(properties.getApiBase(), "/") + "/chat/completions";
        try (cn.hutool.http.HttpResponse response = HttpRequest.post(url)
            .header("Authorization", "Bearer " + properties.getApiKey())
            .timeout(properties.getTimeoutMs())
            .body(body.toString())
            .execute()) {
            String respText = response.body();
            JSONObject resp = JSONUtil.parseObj(respText);
            JSONArray choices = resp.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                log.warn("[ai] LLM 响应缺少 choices: {}", StrUtil.maxLength(respText, 500));
                throw new BusinessException("AI 服务响应异常,请稍后再试");
            }
            return choices.getJSONObject(0).getJSONObject("message").getStr("content", "");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ai] LLM 请求失败: {}", e.getMessage());
            throw new BusinessException("AI 服务暂时不可用,请稍后再试");
        }
    }
}
