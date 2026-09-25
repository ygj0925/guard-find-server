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

package com.guardfind.server.moderation.model.resp;

import com.guardfind.server.moderation.enums.ModerationBizTypeEnum;
import com.guardfind.server.moderation.enums.ReportStatusEnum;
import com.guardfind.server.moderation.enums.ReportTargetTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 举报队列条目视图（后台）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "举报队列条目视图")
public class ReportQueueItemResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 举报 ID
     */
    @Schema(description = "举报 ID", example = "1820000000000000001")
    private Long id;

    /**
     * 举报人（App 用户 ID）
     */
    @Schema(description = "举报人（App 用户 ID）", example = "1789000000000000001")
    private Long reporterId;

    /**
     * 举报人昵称
     */
    @Schema(description = "举报人昵称", example = "无脚星友4821")
    private String reporterNickname;

    /**
     * 目标类型（CASE/LEAD/USER）
     */
    @Schema(description = "目标类型（CASE/LEAD/USER）", example = "CASE")
    private ReportTargetTypeEnum targetType;

    /**
     * 目标 ID
     */
    @Schema(description = "目标 ID", example = "1790000000000000001")
    private Long targetId;

    /**
     * 目标名称
     */
    @Schema(description = "目标名称", example = "张三")
    private String targetName;

    /**
     * 业务类型（用于处置后回跳详情）
     */
    @Schema(description = "业务类型（CASE/LEAD）", example = "CASE")
    private ModerationBizTypeEnum bizType;

    /**
     * 被举报用户（App 用户 ID）
     */
    @Schema(description = "被举报用户（App 用户 ID）", example = "1789000000000000002")
    private Long reportedUserId;

    /**
     * 举报理由
     */
    @Schema(description = "举报理由（字典 gf_report_reason）", example = "FRAUD")
    private String reasonType;

    /**
     * 补充描述
     */
    @Schema(description = "补充描述", example = "要求先转 200 元核实费")
    private String description;

    /**
     * 状态（PENDING/APPROVED/DISMISSED）
     */
    @Schema(description = "状态（PENDING/APPROVED/DISMISSED）", example = "PENDING")
    private ReportStatusEnum status;

    /**
     * 相同目标举报次数
     */
    @Schema(description = "相同目标举报次数", example = "3")
    private Integer hitCount;

    /**
     * 处理说明
     */
    @Schema(description = "处理说明", example = "已下架该档案")
    private String handleRemark;

    /**
     * 处理人（后台用户 ID）
     */
    @Schema(description = "处理人（后台用户 ID）", example = "1")
    private Long handlerId;

    /**
     * 处理时间
     */
    @Schema(description = "处理时间", example = "2026-09-24 15:00:00", type = "string")
    private LocalDateTime handleTime;

    /**
     * 举报时间
     */
    @Schema(description = "举报时间", example = "2026-09-24 12:00:00", type = "string")
    private LocalDateTime createTime;
}
