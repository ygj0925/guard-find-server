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

/**
 * 月报评论实体(挂具体月报,支持楼中楼)
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@TableName("gf_tool_report_comment")
public class ToolReportCommentDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属月报 ID
     */
    private Long entryId;

    /**
     * 评论人用户 ID
     */
    private Long userId;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 回复的评论 ID(楼中楼,可空)
     */
    private Long replyToCommentId;

    /**
     * 回复目标用户 ID(冗余,可空)
     */
    private Long replyToUserId;
}
