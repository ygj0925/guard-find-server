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

import com.guardfind.server.findcase.model.resp.RegionNodeResp;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 广场筛选器元数据（区划树与各字典项，前端 MUST NOT 自行硬编码这些枚举）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "广场筛选器元数据")
public class PlazaFilterResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 行政区划树（省 → 市 → 区县）
     */
    @Schema(description = "行政区划树（省 → 市 → 区县）")
    private List<RegionNodeResp> regions = new ArrayList<>();

    /**
     * 失踪情形（字典 gf_case_type）
     */
    @Schema(description = "失踪情形（字典 gf_case_type）")
    private List<FeedOptionResp> caseTypes = new ArrayList<>();

    /**
     * 年龄段（字典 gf_age_group）
     */
    @Schema(description = "年龄段（字典 gf_age_group）")
    private List<FeedOptionResp> ageGroups = new ArrayList<>();

    /**
     * 血样采集情况（字典 gf_blood_sample_status）
     */
    @Schema(description = "血样采集情况（字典 gf_blood_sample_status）")
    private List<FeedOptionResp> bloodSampleStatuses = new ArrayList<>();

    /**
     * 性别
     */
    @Schema(description = "性别")
    private List<FeedOptionResp> genders = new ArrayList<>();

    /**
     * 与本人关系（字典 gf_relation_type）
     */
    @Schema(description = "与本人关系（字典 gf_relation_type）")
    private List<FeedOptionResp> relations = new ArrayList<>();

    /**
     * 紧急程度
     */
    @Schema(description = "紧急程度")
    private List<FeedOptionResp> urgencyLevels = new ArrayList<>();
}
