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

package com.guardfind.server.moderation.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.guardfind.server.common.base.model.entity.BaseDO;
import com.guardfind.server.moderation.enums.ReportStatusEnum;
import com.guardfind.server.moderation.enums.ReportTargetTypeEnum;
import lombok.Data;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 举报实体
 *
 * <p>
 * 同一举报人对同一目标仅保留一条记录（唯一键 {@code reporter_id + target_type + target_id}），重复举报以
 * {@code hitCount} 累加表达，避免举报队列被刷屏淹没。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@TableName(value = "gf_report", autoResultMap = true)
public class ReportDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 举报人（App 用户 ID）
     */
    private Long reporterId;

    /**
     * 目标类型（CASE/LEAD/USER）
     */
    private ReportTargetTypeEnum targetType;

    /**
     * 目标 ID
     */
    private Long targetId;

    /**
     * 举报理由（字典 gf_report_reason）
     */
    private String reasonType;

    /**
     * 补充描述
     */
    private String description;

    /**
     * 证据图片（JSON 数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> mediaUrls;

    /**
     * 状态（PENDING/APPROVED/DISMISSED）
     */
    private ReportStatusEnum status;

    /**
     * 相同目标举报次数（去重累加）
     */
    private Integer hitCount;

    /**
     * 处理说明
     */
    private String handleRemark;

    /**
     * 处理人（后台用户 ID）
     */
    private Long handlerId;

    /**
     * 处理时间
     */
    private LocalDateTime handleTime;
}
