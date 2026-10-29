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

/**
 * 关键结果(KR)实体
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@TableName("gf_tool_key_result")
public class ToolKeyResultDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属目标 ID
     */
    private Long goalId;

    /**
     * 所属用户 ID(冗余)
     */
    private Long userId;

    /**
     * 关键结果(KR)描述
     */
    private String title;

    /**
     * 目标值
     */
    private BigDecimal targetValue;

    /**
     * 当前值
     */
    private BigDecimal currentValue;

    /**
     * 单位（本/公里/元…）
     */
    private String unit;

    /**
     * 状态（1：进行中；2：已完成）
     */
    private Integer status;

    /**
     * 权重（1-10，目标进度按权重加权）
     */
    private Integer weight;

    /**
     * 信心指数（1-10，更新进度时联动调整）
     */
    private Integer confidence;
}
