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
import java.time.LocalDate;

/**
 * AA 分账响应(perHead 由服务端按「分」取整算好,余数归属口径与客户端展示一致)
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Data
@Schema(description = "AA 分账响应")
public class ToolSplitResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "记录 ID")
    private Long id;

    @Schema(description = "分账事由")
    private String title;

    @Schema(description = "总金额(元)")
    private BigDecimal totalAmount;

    @Schema(description = "参与人数")
    private Integer peopleCount;

    @Schema(description = "每人应付(元,向下取整到分)")
    private BigDecimal perHead;

    @Schema(description = "余数(分,由前 N 人各多承担 1 分)")
    private Long remainderCents;

    @Schema(description = "分账日期")
    private LocalDate splitDate;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "是否已写回记账")
    private Boolean writtenBack;
}
