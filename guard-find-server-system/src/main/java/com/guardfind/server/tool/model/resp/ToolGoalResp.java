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
import java.math.BigDecimal;
import java.util.List;

/**
 * 目标响应(含 KR 与进度)
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@Schema(description = "目标响应")
public class ToolGoalResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "目标 ID")
    private Long id;

    @Schema(description = "目标(O)描述")
    private String title;

    @Schema(description = "周期")
    private String period;

    @Schema(description = "期末复盘评分(0-1,可空)")
    private BigDecimal score;

    @Schema(description = "目标整体进度(0-1,各 KR 均值)")
    private BigDecimal progress;

    @Schema(description = "整体状态(onTrack/atRisk/behind/done)")
    private String goalStatus;

    @Schema(description = "关键结果列表")
    private List<KeyResult> keyResults;

    /**
     * KR 条目
     */
    @Data
    @Schema(description = "KR 条目")
    public static class KeyResult implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "KR ID")
        private Long id;

        @Schema(description = "KR 描述")
        private String title;

        @Schema(description = "目标值")
        private BigDecimal targetValue;

        @Schema(description = "当前值")
        private BigDecimal currentValue;

        @Schema(description = "单位")
        private String unit;

        @Schema(description = "状态(1 进行中 / 2 已完成)")
        private Integer status;

        @Schema(description = "权重(1-10)")
        private Integer weight;

        @Schema(description = "信心指数(1-10)")
        private Integer confidence;

        @Schema(description = "状态(onTrack/atRisk/behind/done)")
        private String krStatus;

        @Schema(description = "进度(0-1)")
        private BigDecimal progress;
    }
}
