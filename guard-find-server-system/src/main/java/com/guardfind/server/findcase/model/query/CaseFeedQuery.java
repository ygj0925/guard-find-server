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

package com.guardfind.server.findcase.model.query;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * 信息流（广场 / 搜索）查询条件
 *
 * <p>
 * 状态过滤不在此处开放：对外可见状态集合固定由 {@code CaseStatusEnum.visiblePublicly()} 与
 * {@code foundVisible()} 决定，避免调用方绕过脱敏与治理策略。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "信息流查询条件")
public class CaseFeedQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 排序方式（latest/urgent/nearby/found）
     */
    @Schema(description = "排序方式（latest/urgent/nearby/found）", example = "latest")
    @Length(max = 16, message = "排序方式长度不能超过 {max} 个字符")
    private String sort;

    /**
     * 失踪省编码
     */
    @Schema(description = "失踪省编码", example = "130000")
    @Length(max = 12, message = "省编码长度不能超过 {max} 个字符")
    private String provinceCode;

    /**
     * 失踪市编码
     */
    @Schema(description = "失踪市编码", example = "130400")
    @Length(max = 12, message = "市编码长度不能超过 {max} 个字符")
    private String cityCode;

    /**
     * 关键词（姓名、别名、失踪地点、体貌特征）
     */
    @Schema(description = "关键词", example = "张三")
    @Length(max = 50, message = "关键词长度不能超过 {max} 个字符")
    private String keyword;

    /**
     * 失踪情形（字典 gf_case_type）
     */
    @Schema(description = "失踪情形（字典 gf_case_type）", example = "MISSING")
    @Length(max = 20, message = "失踪情形长度不能超过 {max} 个字符")
    private String caseType;

    /**
     * 年龄段（字典 gf_age_group）
     */
    @Schema(description = "年龄段（字典 gf_age_group）", example = "0_6")
    @Length(max = 16, message = "年龄段长度不能超过 {max} 个字符")
    private String ageGroup;

    /**
     * 性别（UNKNOWN/MALE/FEMALE）
     */
    @Schema(description = "性别（UNKNOWN/MALE/FEMALE）", example = "MALE")
    @Length(max = 16, message = "性别长度不能超过 {max} 个字符")
    private String gender;

    /**
     * 失踪年份起
     */
    @Schema(description = "失踪年份起", example = "2020")
    @Min(value = 1900, message = "失踪年份起始不能小于 {value}")
    @Max(value = 2200, message = "失踪年份起始不能大于 {value}")
    private Integer yearFrom;

    /**
     * 失踪年份止
     */
    @Schema(description = "失踪年份止", example = "2026")
    @Min(value = 1900, message = "失踪年份截止不能小于 {value}")
    @Max(value = 2200, message = "失踪年份截止不能大于 {value}")
    private Integer yearTo;

    /**
     * 血样采集情况（字典 gf_blood_sample_status）
     */
    @Schema(description = "血样采集情况（字典 gf_blood_sample_status）", example = "COLLECTED")
    @Length(max = 20, message = "血样采集情况长度不能超过 {max} 个字符")
    private String bloodSampleStatus;

    /**
     * 游标（首次请求为空，后续用上一次响应的 nextCursor）
     */
    @Schema(description = "游标", example = "djF8MTcyMTgwMDAwMDAwMHwxNzkwMDAwMDAwMDAwMDAwMA")
    private String cursor;

    /**
     * 每页条数
     */
    @Schema(description = "每页条数", example = "10")
    @Min(value = 1, message = "每页条数不能小于 {value}")
    @Max(value = 50, message = "每页条数不能大于 {value}")
    private Integer size;
}
