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

package com.guardfind.server.moderation.model.query;

import com.guardfind.server.moderation.enums.ReportStatusEnum;
import com.guardfind.server.moderation.enums.ReportTargetTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 举报队列查询条件
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "举报队列查询条件")
public class ReportQueueQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 状态（为空表示待处理 + 已处理全部）
     */
    @Schema(description = "状态（为空表示全部）", example = "PENDING")
    private ReportStatusEnum status;

    /**
     * 目标类型
     */
    @Schema(description = "目标类型（CASE/LEAD/USER）", example = "CASE")
    private ReportTargetTypeEnum targetType;
}
