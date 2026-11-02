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

package com.guardfind.server.property.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.guardfind.server.common.base.model.entity.BaseDO;
import lombok.Data;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 房产房源实体
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@TableName("gf_property")
public class PropertyDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String title;
    private String description;
    private String propertyType;
    private String layout;
    private BigDecimal areaSqm;
    private String floorInfo;
    private String orientation;
    private String decoration;
    private BigDecimal price;
    private String priceType;
    private String address;
    private String district;
    private String city;
    private String facilities;
    private String contactName;
    private String contactPhone;
    private Integer status;
    private Integer viewCount;
    private String images;
}
