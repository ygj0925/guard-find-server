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

package com.guardfind.server.notification.converter;

import cn.hutool.core.collection.CollUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardfind.server.notification.model.NotificationFrame;
import com.guardfind.server.notification.model.entity.AppMessageDO;
import com.guardfind.server.notification.model.resp.AppMessageResp;
import top.continew.starter.json.jackson.exception.JSONException;

import java.util.List;

/**
 * 站内消息视图转换器
 *
 * <p>
 * 列表视图与实时帧共用同一 {@link AppMessageResp} 形态，确保"实时收到的消息"与"拉列表看到的消息"字段完全一致，
 * App 侧无需两套解析。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public final class MessageConverter {

    private MessageConverter() {
    }

    /**
     * 转换为响应参数
     *
     * @param messageDO 消息实体
     * @return 响应参数，入参为 null 时为 null
     */
    public static AppMessageResp toResp(AppMessageDO messageDO) {
        if (messageDO == null) {
            return null;
        }
        AppMessageResp resp = new AppMessageResp();
        resp.setId(messageDO.getId());
        resp.setType(messageDO.getType());
        resp.setTitle(messageDO.getTitle());
        resp.setContent(messageDO.getContent());
        resp.setBizType(messageDO.getBizType());
        resp.setBizId(messageDO.getBizId());
        resp.setPath(messageDO.getPath());
        resp.setReadStatus(Boolean.TRUE.equals(messageDO.getReadStatus()));
        resp.setCreateTime(messageDO.getCreateTime());
        return resp;
    }

    /**
     * 批量转换为响应参数
     *
     * @param messageList 消息实体列表
     * @return 响应参数列表
     */
    public static List<AppMessageResp> toRespList(List<AppMessageDO> messageList) {
        if (CollUtil.isEmpty(messageList)) {
            return List.of();
        }
        return messageList.stream().map(MessageConverter::toResp).toList();
    }

    /**
     * 构造实时帧 JSON（契约 §5：{@code {"type":"message","data":Message}}）
     *
     * <p>
     * 使用容器内的 {@link ObjectMapper}（项目 Jackson 配置）序列化，与 HTTP 响应共用同一套规则：雪花 ID
     * 序列化为字符串、时间为 {@code yyyy-MM-dd HH:mm:ss}，避免 App 侧对同一实体出现两种解析分支。
     * </p>
     *
     * @param objectMapper Jackson 序列化器
     * @param resp         消息响应参数
     * @return 帧 JSON 字符串
     */
    public static String buildMessageFrameJson(ObjectMapper objectMapper, AppMessageResp resp) {
        try {
            return objectMapper.writeValueAsString(NotificationFrame.ofMessage(resp));
        } catch (JsonProcessingException e) {
            throw new JSONException(e);
        }
    }
}
