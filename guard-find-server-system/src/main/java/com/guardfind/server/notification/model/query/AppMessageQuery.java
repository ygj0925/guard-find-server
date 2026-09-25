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

package com.guardfind.server.notification.model.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 站内消息查询参数（契约 §5 {@code GET /app/message}）
 *
 * <p>
 * 不接受 receiverId：收件人恒取当前登录用户，避免越权入参。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "站内消息查询参数")
public class AppMessageQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 是否仅查询未读
     */
    @Schema(description = "是否仅查询未读", example = "false")
    private Boolean onlyUnread;

    /**
     * 消息分组（AUDIT/LEAD/RESULT/SYSTEM）
     */
    @Schema(description = "消息分组（AUDIT/LEAD/RESULT/SYSTEM），不传为全部", example = "AUDIT")
    private String category;
}
