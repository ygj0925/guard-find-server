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

package com.guardfind.server.tool.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 创建签到活动请求
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@Schema(description = "创建签到活动请求")
public class ToolCheckinCreateReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "签到主题", example = "周三晚自习")
    @NotBlank(message = "签到主题不能为空")
    @Length(max = 50, message = "主题不能超过 {max} 个字符")
    private String title;

    @Schema(description = "活动地点名(可空;设置后签到者须填写一致)", example = "图书馆 302")
    @Length(max = 50, message = "地点名不能超过 {max} 个字符")
    private String locationName;

    @Schema(description = "签到开始时间(缺省=现在)", example = "2026-10-29 18:00:00")
    private LocalDateTime startTime;

    @Schema(description = "签到截止时间(缺省=开始后 2 小时)", example = "2026-10-29 20:00:00")
    private LocalDateTime endTime;
}
