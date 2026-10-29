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
import java.time.LocalDateTime;

/**
 * 月报填报实体(一人一活动一份,唯一约束;草稿仅本人可见,提交后全成员可读)
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@TableName("gf_tool_report_entry")
public class ToolReportEntryDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属汇报活动 ID
     */
    private Long activityId;

    /**
     * 填报人用户 ID
     */
    private Long userId;

    /**
     * 汇报正文
     */
    private String content;

    /**
     * 状态（1：草稿；2：已提交）
     */
    private Integer status;

    /**
     * 提交时间(迟交 = 晚于活动 due_date)
     */
    private LocalDateTime submittedAt;
}
