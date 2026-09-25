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

package com.guardfind.server.notification.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.guardfind.server.notification.support.NotificationConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 实时通道帧（契约 §5「WebSocket 帧」）
 *
 * <p>
 * 帧结构固定为 {@code {"type":"message","data":Message}} 与 {@code {"type":"ping"}/{"type":"pong"}}；
 * {@code data} 为 null 时不参与序列化，确保心跳帧不会被写成 {@code "data":null} 而抬高 App 解析成本。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "实时通道帧")
public class NotificationFrame implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 帧类型（message/ping/pong）
     */
    @Schema(description = "帧类型（message：定向站内信；ping：心跳；pong：心跳响应）", example = "message")
    private String type;

    /**
     * 帧数据（站内信帧为 Message，心跳帧为空）
     */
    @Schema(description = "帧数据（站内信帧为 Message，心跳帧为空）")
    private Object data;

    public NotificationFrame() {
    }

    public NotificationFrame(String type, Object data) {
        this.type = type;
        this.data = data;
    }

    /**
     * 构造定向站内信帧
     *
     * @param message 消息体
     * @return 帧
     */
    public static NotificationFrame ofMessage(Object message) {
        return new NotificationFrame(NotificationConstants.FRAME_TYPE_MESSAGE, message);
    }

    /**
     * 构造心跳响应帧
     *
     * @return 帧
     */
    public static NotificationFrame pong() {
        return new NotificationFrame(NotificationConstants.FRAME_TYPE_PONG, null);
    }
}
