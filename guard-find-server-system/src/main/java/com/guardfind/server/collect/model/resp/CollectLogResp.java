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
import java.time.LocalDateTime;

/**
 * 采集批次日志响应参数
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "采集批次日志响应参数")
public class CollectLogResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "日志 ID", example = "1")
    private Long id;

    @Schema(description = "批次号", example = "20260924090012345")
    private String batchNo;

    @Schema(description = "采集源标识", example = "rangaihuijia")
    private String sourceCode;

    @Schema(description = "触发方式", example = "JOB")
    private String triggerType;

    @Schema(description = "抓取列表页数", example = "5")
    private Integer pages;

    @Schema(description = "抓取详情数", example = "20")
    private Integer fetched;

    @Schema(description = "新增候选数", example = "18")
    private Integer added;

    @Schema(description = "重复数", example = "2")
    private Integer duplicated;

    @Schema(description = "失败数", example = "1")
    private Integer failed;

    @Schema(description = "跳过/中止原因", example = "robots.txt 对 User-agent: * 禁止抓取路径 /xunren/：本轮中止")
    private String skippedReason;

    @Schema(description = "耗时（毫秒）", example = "42000")
    private Long costMs;

    @Schema(description = "实测最小同域间隔（毫秒）", example = "2005")
    private Integer minIntervalMs;

    @Schema(description = "错误摘要")
    private String errorSummary;

    @Schema(description = "是否成功", example = "true")
    private Boolean success;

    @Schema(description = "创建时间", example = "2026-09-24 09:00:42", type = "string")
    private LocalDateTime createTime;
}
