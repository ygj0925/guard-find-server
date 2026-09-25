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

package com.guardfind.server.app.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * C 端账号注销申请响应参数
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "C 端账号注销申请响应参数")
public class AppDeactivateResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 冷静期天数
     */
    @Schema(description = "冷静期天数", example = "15")
    private Integer cooldownDays;

    /**
     * 冷静期截止时间（期内登录可撤销）
     */
    @Schema(description = "冷静期截止时间", example = "2026-10-09 10:00:00", type = "string")
    private LocalDateTime deadline;
}
