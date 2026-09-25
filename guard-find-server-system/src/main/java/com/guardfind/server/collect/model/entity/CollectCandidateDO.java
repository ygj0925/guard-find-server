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

package com.guardfind.server.collect.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.guardfind.server.collect.enums.CandidateStatusEnum;
import com.guardfind.server.common.base.model.entity.BaseDO;
import com.guardfind.server.common.enums.GenderEnum;
import lombok.Data;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采集候选实体
 *
 * <p>
 * 归一化后的站外寻亲信息，未审核前 MUST NOT 进入广场或搜索。字段集限于寻亲必要信息，
 * {@code sourceCode/sourceId/sourceUrl/fetchTime/rawId} 支撑来源追溯与批量撤回；
 * {@code dedupeKey} 相同者保留信息最完整者为主候选，其余转 {@code DUPLICATED} 并指向主候选。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@TableName(value = "gf_collect_candidate", autoResultMap = true)
public class CollectCandidateDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 采集源标识
     */
    private String sourceCode;

    /**
     * 源侧编号
     */
    private String sourceId;

    /**
     * 来源页面链接
     */
    private String sourceUrl;

    /**
     * 抓取时间
     */
    private LocalDateTime fetchTime;

    /**
     * 原文快照 ID
     */
    private Long rawId;

    /**
     * 源侧类别原文
     */
    private String categoryText;

    /**
     * 姓名（规范化后）
     */
    private String name;

    /**
     * 别名（JSON 数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> aliases;

    /**
     * 性别
     */
    private GenderEnum gender;

    /**
     * 出生日期（归一到日）
     */
    private LocalDate birthday;

    /**
     * 失踪时年龄
     */
    private Integer ageAtMissing;

    /**
     * 失踪时间（归一到日）
     */
    private LocalDateTime missingTime;

    /**
     * 失踪省编码
     */
    private String provinceCode;

    /**
     * 失踪市编码
     */
    private String cityCode;

    /**
     * 失踪区县编码
     */
    private String districtCode;

    /**
     * 区划原文
     */
    private String regionText;

    /**
     * 失踪地址原文
     */
    private String address;

    /**
     * 失踪时身高（cm）
     */
    private Integer height;

    /**
     * 特征描述
     */
    private String features;

    /**
     * 发生经过
     */
    private String narrative;

    /**
     * 家庭背景及线索资料
     */
    private String familyClues;

    /**
     * 其他说明
     */
    private String otherNote;

    /**
     * 源页面公开的寻亲联系人原文
     */
    private String contactText;

    /**
     * 照片地址（审批时本地化）
     */
    private String coverUrl;

    /**
     * 归一化后的失踪情形（字典 gf_case_type）
     */
    private String caseType;

    /**
     * 血样采集情况（字典 gf_blood_sample_status）
     */
    private String bloodSampleStatus;

    /**
     * 去重指纹
     */
    private String dedupeKey;

    /**
     * 字段完整度（0-1）
     */
    private BigDecimal completeness;

    /**
     * 状态
     */
    private CandidateStatusEnum status;

    /**
     * 主候选 ID（DUPLICATED 时指向）
     */
    private Long primaryId;

    /**
     * 审批/合并产生的档案 ID
     */
    private Long caseId;

    /**
     * 驳回理由
     */
    private String rejectReason;

    /**
     * 批次号
     */
    private String batchNo;
}
