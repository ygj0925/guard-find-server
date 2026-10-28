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

package com.guardfind.server.tool.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 习惯响应(streak 口径服务端统一:今天没打卡时连续天数按昨天截止,不清零)
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Data
@Schema(description = "习惯响应")
public class ToolHabitResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "习惯 ID")
    private Long id;

    @Schema(description = "习惯名称")
    private String title;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "今天是否已打卡")
    private Boolean todayChecked;

    @Schema(description = "当前连续天数")
    private Long currentStreak;

    @Schema(description = "历史最长连续天数")
    private Long bestStreak;

    @Schema(description = "累计打卡次数")
    private Long totalDays;
}
