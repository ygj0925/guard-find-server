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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 任务响应
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@Schema(description = "任务响应")
public class ToolTaskResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "任务 ID")
    private Long id;

    @Schema(description = "所属家庭组 ID")
    private Long groupId;

    @Schema(description = "任务标题")
    private String title;

    @Schema(description = "详情")
    private String detail;

    @Schema(description = "负责人用户 ID")
    private Long assigneeUserId;

    @Schema(description = "负责人昵称")
    private String assigneeNickname;

    @Schema(description = "创建人用户 ID")
    private Long creatorUserId;

    @Schema(description = "截止日期")
    private LocalDate dueDate;

    @Schema(description = "优先级(1 高 / 2 中 / 3 低)")
    private Integer priority;

    @Schema(description = "状态(1 待办 / 2 进行中 / 3 已完成)")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
