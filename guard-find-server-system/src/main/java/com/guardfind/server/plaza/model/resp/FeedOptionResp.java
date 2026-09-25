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

package com.guardfind.server.plaza.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 筛选项（前端不硬编码枚举，全部由此结构下发）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "筛选项")
public class FeedOptionResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 取值
     */
    @Schema(description = "取值", example = "TRAFFICKING")
    private String value;

    /**
     * 展示名称
     */
    @Schema(description = "展示名称", example = "疑似被拐")
    private String label;

    /**
     * 排序（小的在前）
     */
    @Schema(description = "排序", example = "1")
    private Integer order;

    /**
     * 构造筛选项
     *
     * @param value 取值
     * @param label 展示名称
     * @param order 排序
     * @return 筛选项
     */
    public static FeedOptionResp of(String value, String label, Integer order) {
        FeedOptionResp option = new FeedOptionResp();
        option.setValue(value);
        option.setLabel(label);
        option.setOrder(order);
        return option;
    }
}
