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

package com.guardfind.server.notification.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 通知偏好单项请求参数
 *
 * <p>
 * PUT 为"整项覆盖"语义：{@code smsEnabled} 未传即视为关闭短信，避免 App 漏传导致历史开启状态被静默保留。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "通知偏好单项请求参数")
public class PreferenceItemReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 事件类型
     */
    @NotBlank(message = "通知类型不能为空")
    @Size(max = 24, message = "通知类型长度不能超过 {max} 个字符")
    @Schema(description = "事件类型（必填）", example = "CASE_LEAD")
    private String type;

    /**
     * 站内信是否开启
     */
    @NotNull(message = "站内信开关不能为空")
    @Schema(description = "站内信是否开启（必填；强制类事件传 false 将被拒绝）", example = "true")
    private Boolean inboxEnabled;

    /**
     * 短信是否开启
     */
    @Schema(description = "短信是否开启（默认关闭，仅显式开启后发送）", example = "false")
    private Boolean smsEnabled;
}
