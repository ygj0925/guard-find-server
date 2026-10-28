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

package com.guardfind.server.ledger.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.guardfind.server.common.base.model.entity.BaseDO;
import lombok.Data;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 记账记录实体
 *
 * <p>
 * 工具箱-记账的收支流水。金额固定为正数，方向由 {@code type} 表达；
 * {@code recordDate} 是用户声明的记账日期（可与创建时间不同天）。
 * </p>
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Data
@TableName("gf_ledger_record")
public class LedgerRecordDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属用户 ID
     */
    private Long userId;

    /**
     * 所属家庭组(空=个人账本)
     */
    private Long groupId;

    /**
     * 类型（1：支出；2：收入）
     */
    private Integer type;

    /**
     * 金额（元，正数）
     */
    private BigDecimal amount;

    /**
     * 分类码（food/transport/shopping/...）
     */
    private String category;

    /**
     * 记账日期
     */
    private LocalDate recordDate;

    /**
     * 备注
     */
    private String remark;
}
