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

/**
 * 物品收纳实体
 *
 * @author guardfind
 * @since 2026/11/06
 */
@Data
@TableName("gf_tool_item")
public class ToolItemDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属用户 ID
     */
    private Long userId;

    /**
     * 物品名称
     */
    private String name;

    /**
     * 存放位置
     */
    private String location;

    /**
     * 数量
     */
    private Integer quantity;

    /**
     * 备注
     */
    private String note;

    /**
     * 是否归档（0：否；1：是）
     */
    private Integer archived;

    /**
     * 版本号，用于乐观锁
     */
    private Integer version;
}
