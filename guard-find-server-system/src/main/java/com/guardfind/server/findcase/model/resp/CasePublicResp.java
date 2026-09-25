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

import com.guardfind.server.findcase.enums.CaseStatusEnum;
import com.guardfind.server.findcase.enums.UrgencyLevelEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 寻人档案公开视图（广场卡片与公开详情共用）
 *
 * <p>
 * 该类型是脱敏边界：MUST NOT 出现详细地址（address）、联系方式（contactPhone）与精确到日的失踪时间
 * （missingTime）。区划只到市，失踪时间只到月，姓名与体貌特征按发布人自愿公开的范围展示。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "寻人档案公开视图（脱敏）")
public class CasePublicResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 档案 ID
     */
    @Schema(description = "档案 ID", example = "1790000000000000000")
    private Long id;

    /**
     * 姓名
     */
    @Schema(description = "姓名", example = "张三")
    private String name;

    /**
     * 性别（UNKNOWN/MALE/FEMALE）
     */
    @Schema(description = "性别（UNKNOWN/MALE/FEMALE）", example = "MALE")
    private String gender;

    /**
     * 年龄段（字典 gf_age_group）
     */
    @Schema(description = "年龄段（字典 gf_age_group）", example = "0_6")
    private String ageGroup;

    /**
     * 失踪时年龄
     */
    @Schema(description = "失踪时年龄", example = "6")
    private Integer ageAtMissing;

    /**
     * 别名
     */
    @Schema(description = "别名", example = "小虎")
    private List<String> aliases;

    /**
     * 失踪年月（只到月，格式 yyyy-MM）
     */
    @Schema(description = "失踪年月（格式 yyyy-MM）", example = "2024-08")
    private String missingMonth;

    /**
     * 失踪地区划文本（只到市）
     */
    @Schema(description = "失踪地区划文本（只到市）", example = "河北省 邯郸市")
    private String regionText;

    /**
     * 失踪区县名称（公开详情精确到区）
     */
    @Schema(description = "失踪区县名称", example = "曲周县")
    private String districtText;

    /**
     * 失踪时身高（cm）
     */
    @Schema(description = "失踪时身高（cm）", example = "118")
    private Integer height;

    /**
     * 疤痕/胎记等体貌特征
     */
    @Schema(description = "疤痕/胎记等体貌特征", example = "左眉骨有 2cm 旧疤")
    private String features;

    /**
     * 失踪情形（字典 gf_case_type）
     */
    @Schema(description = "失踪情形（字典 gf_case_type）", example = "MISSING")
    private String caseType;

    /**
     * 紧急程度
     */
    @Schema(description = "紧急程度", example = "NORMAL")
    private UrgencyLevelEnum urgencyLevel;

    /**
     * 已失踪时长（小时）
     */
    @Schema(description = "已失踪时长（小时）", example = "18720")
    private Long missingHours;

    /**
     * 状态
     */
    @Schema(description = "状态", example = "PUBLISHED")
    private CaseStatusEnum status;

    /**
     * 缩略图地址
     */
    @Schema(description = "缩略图地址", example = "https://cdn.tianxiawuguai.com/case/2026/09/24/a.jpg")
    private String coverUrl;

    /**
     * 影像地址列表
     */
    @Schema(description = "影像地址列表")
    private List<String> mediaUrls;

    /**
     * 线索数
     */
    @Schema(description = "线索数", example = "3")
    private Integer leadCount;

    /**
     * 来源（APP/COLLECT/IMPORT）
     */
    @Schema(description = "来源（APP/COLLECT/IMPORT）", example = "APP")
    private String sourceType;

    /**
     * 来源页面链接
     */
    @Schema(description = "来源页面链接", example = "https://www.rangaihuijia.com/xunren/view/143036")
    private String sourceUrl;

    /**
     * 发布时间
     */
    @Schema(description = "发布时间", example = "2026-09-24 10:20:00", type = "string")
    private LocalDateTime publishTime;

    /**
     * 联系方式是否已隐藏（公开视图恒为 true）
     */
    @Schema(description = "联系方式是否已隐藏", example = "true")
    private Boolean ownerMasked = true;
}
