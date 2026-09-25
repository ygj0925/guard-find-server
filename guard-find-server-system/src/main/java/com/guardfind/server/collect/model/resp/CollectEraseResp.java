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

package com.guardfind.server.collect.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 采集数据撤回执行结果
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "采集数据撤回执行结果")
public class CollectEraseResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "撤回审计 ID", example = "1")
    private Long eraseLogId;

    @Schema(description = "命中候选数", example = "12")
    private int candidateCount;

    @Schema(description = "连带清除的档案数", example = "3")
    private int caseCount;

    @Schema(description = "受影响候选 ID")
    private List<Long> candidateIds;

    @Schema(description = "受影响档案 ID")
    private List<Long> caseIds;

    @Schema(description = "执行结果", example = "已清除候选 12 条、档案 3 条")
    private String result;
}
