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

import com.guardfind.server.collect.enums.CandidateStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 采集候选查询条件
 *
 * <p>
 * 候选列表为审核工作台入口，条件由业务实现显式拼装（不依赖自动映射），避免"忘加状态过滤导致把已清除条目也列出来"。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "采集候选查询条件")
public class CandidateQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 状态
     */
    @Schema(description = "状态", example = "PENDING")
    private CandidateStatusEnum status;

    /**
     * 采集源标识
     */
    @Schema(description = "采集源标识", example = "rangaihuijia")
    private String sourceCode;

    /**
     * 失踪省编码
     */
    @Schema(description = "失踪省编码", example = "130000")
    private String provinceCode;

    /**
     * 失踪市编码
     */
    @Schema(description = "失踪市编码", example = "130100")
    private String cityCode;

    /**
     * 批次号
     */
    @Schema(description = "批次号", example = "20260924090012")
    private String batchNo;

    /**
     * 关键词（姓名、特征描述、地址与区划原文）
     */
    @Schema(description = "关键词", example = "张三")
    private String keyword;
}
