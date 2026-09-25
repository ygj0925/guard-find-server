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
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 待审核队列条目视图
 *
 * <p>
 * 队列把不同业务对象收敛成同一形状，审核人只看"是谁提交的、提交了什么、有多急"，
 * 详情仍回各业务对象的读接口取，避免此处复制一套易漂移的字段。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "待审核队列条目视图")
public class ModerationItemResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 业务类型（CASE/LEAD）
     */
    @Schema(description = "业务类型（CASE/LEAD）", example = "CASE")
    private ModerationBizTypeEnum bizType;

    /**
     * 业务 ID
     */
    @Schema(description = "业务 ID", example = "1790000000000000001")
    private Long bizId;

    /**
     * 标题（档案姓名 / 线索所属档案）
     */
    @Schema(description = "标题", example = "张三")
    private String title;

    /**
     * 摘要（特征描述或线索描述节选）
     */
    @Schema(description = "摘要", example = "左眉骨有 2cm 旧疤")
    private String summary;

    /**
     * 待审状态编码
     */
    @Schema(description = "待审状态编码", example = "PENDING_REVIEW")
    private String statusCode;

    /**
     * 待审状态描述
     */
    @Schema(description = "待审状态描述", example = "待审核")
    private String statusDescription;

    /**
     * 提交时间（队列按此升序）
     */
    @Schema(description = "提交时间", example = "2026-09-24 09:00:00", type = "string")
    private LocalDateTime submitTime;

    /**
     * 省编码
     */
    @Schema(description = "省编码", example = "130000")
    private String provinceCode;

    /**
     * 市编码
     */
    @Schema(description = "市编码", example = "130400")
    private String cityCode;

    /**
     * 关联 C 端用户 ID（档案发布人或线索提交人）
     */
    @Schema(description = "关联 C 端用户 ID", example = "1789000000000000001")
    private Long relatedUserId;

    /**
     * 是否高风险（线索命中诈骗话术）
     */
    @Schema(description = "是否高风险（线索命中诈骗话术）", example = "true")
    private Boolean highRisk;

    /**
     * 被举报次数（档案）
     */
    @Schema(description = "被举报次数", example = "3")
    private Integer reportCount;

    /**
     * 线索数（档案）
     */
    @Schema(description = "线索数", example = "5")
    private Integer leadCount;
}
