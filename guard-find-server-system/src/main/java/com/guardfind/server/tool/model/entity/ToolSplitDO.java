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

package com.guardfind.server.tool.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.guardfind.server.common.base.model.entity.BaseDO;
import lombok.Data;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * AA 分账记录实体(计算在客户端实时做,这里只存结果事实;写回记账由客户端调 ledger 接口)
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Data
@TableName("gf_tool_split")
public class ToolSplitDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属用户 ID
     */
    private Long userId;

    /**
     * 分账事由
     */
    private String title;

    /**
     * 总金额(元)
     */
    private BigDecimal totalAmount;

    /**
     * 参与人数
     */
    private Integer peopleCount;

    /**
     * 分账日期
     */
    private LocalDate splitDate;

    /**
     * 备注
     */
    private String remark;

    /**
     * 是否已写回记账(0：否；1：是)
     */
    private Integer writtenBack;
}
