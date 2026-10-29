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

package com.guardfind.server.tool.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.guardfind.server.common.base.model.entity.BaseDO;
import lombok.Data;

import java.io.Serial;
import java.time.LocalDate;

/**
 * 家庭组任务分派实体
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@TableName("gf_tool_task")
public class ToolTaskDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属家庭组 ID
     */
    private Long groupId;

    /**
     * 创建人用户 ID
     */
    private Long creatorUserId;

    /**
     * 负责人用户 ID(组成员)
     */
    private Long assigneeUserId;

    /**
     * 任务标题
     */
    private String title;

    /**
     * 详情
     */
    private String detail;

    /**
     * 截止日期
     */
    private LocalDate dueDate;

    /**
     * 优先级（1：高；2：中；3：低）
     */
    private Integer priority;

    /**
     * 状态（1：待办；2：进行中；3：已完成）
     */
    private Integer status;

    /**
     * 关联的 KR ID（可空；用于任务进度自动推进 KR）
     */
    private Long krId;
}
