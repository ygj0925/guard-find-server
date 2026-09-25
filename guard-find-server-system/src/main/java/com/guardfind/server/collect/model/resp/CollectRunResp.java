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
import java.util.ArrayList;
import java.util.List;

/**
 * 采集批次执行结果
 *
 * <p>
 * 契约 {@code POST /system/collect/run/{sourceCode}} 的 data 形态。robots 拒绝时各计数为 0，
 * 但 {@code success=false} 且 {@code skippedReason} 必须留下中止原因——"没采"也要可查。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "采集批次执行结果")
public class CollectRunResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "批次号", example = "20260924090012345")
    private String batchNo;

    @Schema(description = "采集源标识", example = "rangaihuijia")
    private String sourceCode;

    @Schema(description = "抓取列表页数", example = "5")
    private int pages;

    @Schema(description = "抓取详情数", example = "20")
    private int fetched;

    @Schema(description = "新增候选数", example = "18")
    private int added;

    @Schema(description = "重复（收敛）数", example = "2")
    private int duplicated;

    @Schema(description = "失败数", example = "1")
    private int failed;

    @Schema(description = "耗时（毫秒）", example = "42000")
    private long costMs;

    @Schema(description = "实测最小同域间隔（毫秒）", example = "2005")
    private Integer minIntervalMs;

    @Schema(description = "是否成功", example = "true")
    private boolean success = true;

    @Schema(description = "跳过/中止原因", example = "robots.txt 无法获取：本轮中止")
    private String skippedReason;

    @Schema(description = "失败明细（截断后的错误清单）")
    private List<String> errors = new ArrayList<>();
}
