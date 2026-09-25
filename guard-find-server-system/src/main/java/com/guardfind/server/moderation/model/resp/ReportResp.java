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

import com.guardfind.server.moderation.enums.ReportStatusEnum;
import com.guardfind.server.moderation.enums.ReportTargetTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 举报视图
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "举报视图")
public class ReportResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 举报 ID
     */
    @Schema(description = "举报 ID", example = "1820000000000000001")
    private Long id;

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
     * 目标名称（档案姓名/线索摘要/用户昵称，按目标类型给出）
     */
    @Schema(description = "目标名称", example = "张三")
    private String targetName;

    /**
     * 举报理由（字典 gf_report_reason）
     */
    @Schema(description = "举报理由（字典 gf_report_reason）", example = "FRAUD")
    private String reasonType;

    /**
     * 补充描述
     */
    @Schema(description = "补充描述", example = "要求先转 200 元核实费")
    private String description;

    /**
     * 证据图片
     */
    @Schema(description = "证据图片")
    private List<String> mediaUrls = new ArrayList<>();

    /**
     * 状态（PENDING/APPROVED/DISMISSED）
     */
    @Schema(description = "状态（PENDING/APPROVED/DISMISSED）", example = "PENDING")
    private ReportStatusEnum status;

    /**
     * 相同目标举报次数（去重累加）
     */
    @Schema(description = "相同目标举报次数", example = "3")
    private Integer hitCount;

    /**
     * 处理说明
     */
    @Schema(description = "处理说明", example = "已下架该档案并对发布人禁言 7 天")
    private String handleRemark;

    /**
     * 举报时间
     */
    @Schema(description = "举报时间", example = "2026-09-24 12:00:00", type = "string")
    private LocalDateTime createTime;
}
