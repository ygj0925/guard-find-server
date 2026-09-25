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

package com.guardfind.server.notification.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 未读消息数响应参数（契约 §5 {@code GET /app/message/unread}）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "未读消息数响应参数")
public class UnreadResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 未读数量
     */
    @Schema(description = "未读数量", example = "3")
    private Long unreadCount;

    public UnreadResp() {
    }

    public UnreadResp(Long unreadCount) {
        this.unreadCount = unreadCount;
    }
}
