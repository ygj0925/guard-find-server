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
 * 通知偏好响应参数（契约 §5 Preference）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "通知偏好响应参数")
public class PreferenceResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 事件类型
     */
    @Schema(description = "事件类型（CASE_APPROVED/CASE_REJECTED/CASE_LEAD/LEAD_REPLIED/CASE_FOUND/CASE_EXPIRING/REPORT_RESULT/CASE_PUBLISHED/SYSTEM）", example = "CASE_LEAD")
    private String type;

    /**
     * 偏好项名称
     */
    @Schema(description = "偏好项名称", example = "新线索提醒")
    private String label;

    /**
     * 站内信是否开启
     */
    @Schema(description = "站内信是否开启", example = "true")
    private Boolean inboxEnabled;

    /**
     * 站内信是否强制开启（true 时 App 侧开关置灰，不可关闭）
     */
    @Schema(description = "站内信是否强制开启（true 时不可关闭）", example = "false")
    private Boolean inboxForced;

    /**
     * 短信是否开启
     */
    @Schema(description = "短信是否开启", example = "false")
    private Boolean smsEnabled;

    /**
     * 短信说明
     */
    @Schema(description = "短信说明", example = "短信由运营商计费")
    private String smsNote;
}
