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

package com.guardfind.server.collect.model.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import top.continew.starter.data.annotation.Query;
import top.continew.starter.data.enums.QueryType;

import java.io.Serial;
import java.io.Serializable;

/**
 * 采集源查询条件
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "采集源查询条件")
public class CollectSourceQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 源标识
     */
    @Schema(description = "源标识", example = "rangaihuijia")
    @Query(type = QueryType.EQ)
    private String code;

    /**
     * 源名称
     */
    @Schema(description = "源名称", example = "公开寻亲库")
    @Query(type = QueryType.LIKE)
    private String name;

    /**
     * 是否启用
     */
    @Schema(description = "是否启用", example = "true")
    @Query(type = QueryType.EQ)
    private Boolean enabled;
}
