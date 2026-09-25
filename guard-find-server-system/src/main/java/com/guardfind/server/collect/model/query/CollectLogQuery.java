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

package com.guardfind.server.collect.model.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 采集批次日志查询条件
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "采集批次日志查询条件")
public class CollectLogQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 采集源标识
     */
    @Schema(description = "采集源标识", example = "rangaihuijia")
    private String sourceCode;

    /**
     * 批次号
     */
    @Schema(description = "批次号", example = "20260924090012")
    private String batchNo;

    /**
     * 触发方式（JOB/MANUAL）
     */
    @Schema(description = "触发方式", example = "JOB")
    private String triggerType;

    /**
     * 是否成功
     */
    @Schema(description = "是否成功", example = "true")
    private Boolean success;
}
