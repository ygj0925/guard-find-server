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

package com.guardfind.server.findcase.model.resp;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 行政区划节点（广场筛选器用的省-市-区三级树，叶子节点不输出 children 字段）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "行政区划节点")
public class RegionNodeResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 区划编码（6 位）
     */
    @Schema(description = "区划编码", example = "130400")
    private String code;

    /**
     * 名称
     */
    @Schema(description = "名称", example = "邯郸市")
    private String name;

    /**
     * 层级（province/city/district）
     */
    @Schema(description = "层级（province/city/district）", example = "city")
    private String level;

    /**
     * 下级区划
     */
    @Schema(description = "下级区划")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<RegionNodeResp> children;
}
