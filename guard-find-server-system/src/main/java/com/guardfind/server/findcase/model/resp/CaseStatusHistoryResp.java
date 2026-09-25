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

package com.guardfind.server.findcase.model.resp;

import com.guardfind.server.findcase.enums.CaseStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 寻人档案状态轨迹项（完整视图内联返回，便于家属说明"信息被谁改过"）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "寻人档案状态轨迹项")
public class CaseStatusHistoryResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 变更前状态
     */
    @Schema(description = "变更前状态", example = "PENDING_REVIEW")
    private CaseStatusEnum from;

    /**
     * 变更后状态
     */
    @Schema(description = "变更后状态", example = "PUBLISHED")
    private CaseStatusEnum to;

    /**
     * 原因说明
     */
    @Schema(description = "原因说明", example = "审核通过")
    private String reason;

    /**
     * 变更时间
     */
    @Schema(description = "变更时间", example = "2026-09-24 10:20:00", type = "string")
    private LocalDateTime time;

    /**
     * 操作人
     */
    @Schema(description = "操作人", example = "无脚星友4821")
    private String operator;
}
