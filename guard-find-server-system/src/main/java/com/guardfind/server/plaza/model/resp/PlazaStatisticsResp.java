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
 * 广场统计概览（首页"累计帮助 N 个家庭"类展示位）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "广场统计概览")
public class PlazaStatisticsResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 累计建档数（不含已删除档案）
     */
    @Schema(description = "累计建档数", example = "128430")
    private Long totalCase;

    /**
     * 已找回数
     */
    @Schema(description = "已找回数", example = "3210")
    private Long foundCase;

    /**
     * 近 30 日新增数
     */
    @Schema(description = "近 30 日新增数", example = "512")
    private Long recentNewCase;

    /**
     * 今日新增线索数
     */
    @Schema(description = "今日新增线索数", example = "87")
    private Long todayLead;
}
