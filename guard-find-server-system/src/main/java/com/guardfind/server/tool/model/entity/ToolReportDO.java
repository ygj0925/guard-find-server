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
 * 月度工作汇报活动实体
 *
 * <p>
 * 权限模型(三种角色,见 ToolReportMemberDO.role):
 * 发起人=创建/管理/查看全部;填报人=写自己那份月报;接收人=只读。
 * 全部成员可便捷阅读已提交的月报并评论回复;草稿仅本人可见。
 * </p>
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@TableName("gf_tool_report")
public class ToolReportDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 发起人用户 ID
     */
    private Long userId;

    /**
     * 汇报标题
     */
    private String title;

    /**
     * 汇报周期(yyyy-MM)
     */
    private String period;

    /**
     * 提交截止时间(可空;超期提交标记迟交)
     */
    private LocalDateTime dueDate;
}
