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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 通知偏好更新请求参数（契约 §5 {@code PUT /app/notification/preference}）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "通知偏好更新请求参数")
public class PreferenceUpdateReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 偏好项列表
     */
    @Valid
    @NotEmpty(message = "请至少设置一项通知偏好")
    @Size(max = 20, message = "单次最多设置 {max} 项通知偏好")
    @Schema(description = "偏好项列表")
    private List<PreferenceItemReq> items;
}
