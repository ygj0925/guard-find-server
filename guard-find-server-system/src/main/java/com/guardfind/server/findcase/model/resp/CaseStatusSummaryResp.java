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
 * 我的发布各状态计数（用于"我的"页状态角标）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "我的发布各状态计数")
public class CaseStatusSummaryResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 草稿数
     */
    @Schema(description = "草稿数", example = "1")
    private Long draft = 0L;

    /**
     * 待审核数
     */
    @Schema(description = "待审核数", example = "1")
    private Long pendingReview = 0L;

    /**
     * 已发布数
     */
    @Schema(description = "已发布数", example = "6")
    private Long published = 0L;

    /**
     * 已驳回数
     */
    @Schema(description = "已驳回数", example = "1")
    private Long rejected = 0L;

    /**
     * 已归档数
     */
    @Schema(description = "已归档数", example = "2")
    private Long archived = 0L;

    /**
     * 已找回数
     */
    @Schema(description = "已找回数", example = "1")
    private Long found = 0L;

    /**
     * 已下架数
     */
    @Schema(description = "已下架数", example = "0")
    private Long offline = 0L;
}
