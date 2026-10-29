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
 * 宠物档案实体
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@TableName("gf_pet")
public class ToolPetDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String name;
    private String species;
    private String breed;
    private Integer gender;
    private LocalDate birthday;
    private String avatar;
    private BigDecimal weightKg;
    private String notes;
}
