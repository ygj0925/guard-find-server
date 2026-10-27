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

package com.guardfind.server.ledger.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 记账记录响应
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Data
@Schema(description = "记账记录响应")
public class LedgerRecordResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "记录 ID")
    private Long id;

    @Schema(description = "类型（1：支出；2：收入）")
    private Integer type;

    @Schema(description = "金额（元）")
    private BigDecimal amount;

    @Schema(description = "分类码")
    private String category;

    @Schema(description = "记账日期")
    private LocalDate recordDate;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
