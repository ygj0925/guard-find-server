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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 疑似重复档案项（提交审核命中重复规则时返回，供用户核对后决定 forceSubmit 或改走合并流程）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "疑似重复档案项")
public class CaseSimilarResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 档案 ID
     */
    @Schema(description = "档案 ID", example = "1790000000000000000")
    private Long id;

    /**
     * 档案编号
     */
    @Schema(description = "档案编号", example = "WG260924-2QK3X9ZT")
    private String archiveNo;

    /**
     * 姓名
     */
    @Schema(description = "姓名", example = "张三")
    private String name;

    /**
     * 失踪年月
     */
    @Schema(description = "失踪年月（格式 yyyy-MM）", example = "2024-08")
    private String missingMonth;

    /**
     * 失踪地区划文本（只到市）
     */
    @Schema(description = "失踪地区划文本（只到市）", example = "河北省 邯郸市")
    private String regionText;

    /**
     * 状态
     */
    @Schema(description = "状态", example = "PUBLISHED")
    private String status;
}
