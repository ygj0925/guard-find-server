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

package com.guardfind.server.findcase.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.guardfind.server.common.base.model.entity.BaseDO;
import com.guardfind.server.common.enums.GenderEnum;
import com.guardfind.server.findcase.enums.CaseStatusEnum;
import com.guardfind.server.findcase.enums.UrgencyLevelEnum;
import lombok.Data;
import top.continew.starter.encrypt.field.annotation.FieldEncrypt;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 寻人档案实体
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@TableName(value = "gf_find_case", autoResultMap = true)
public class FindCaseDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 档案编号（对外展示）
     */
    private String caseNo;

    /**
     * 发布人（App 用户 ID；采集来源为空）
     */
    private Long ownerId;

    /**
     * 状态
     */
    private CaseStatusEnum status;

    /**
     * 紧急程度
     */
    private UrgencyLevelEnum urgencyLevel;

    /**
     * 与本人关系（字典 gf_relation_type）
     */
    private String relationType;

    /**
     * 姓名
     */
    private String name;

    /**
     * 性别
     */
    private GenderEnum gender;

    /**
     * 出生日期
     */
    private LocalDate birthday;

    /**
     * 失踪时年龄
     */
    private Integer ageAtMissing;

    /**
     * 年龄段（字典 gf_age_group）
     */
    private String ageGroup;

    /**
     * 失踪时间
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
     * 详细地址（公开视图不返回）
     */
    private String address;

    /**
     * 户籍地区划编码
     */
    private String hometownCode;

    /**
     * 经度（一期不采集，预留）
     */
    private BigDecimal lng;

    /**
     * 纬度（一期不采集，预留）
     */
    private BigDecimal lat;

    /**
     * 失踪时身高（cm）
     */
    private Integer height;

    /**
     * 体型
     */
    private String bodyType;

    /**
     * 发型
     */
    private String hairType;

    /**
     * 口音
     */
    private String accent;

    /**
     * 疤痕/胎记等体貌特征
     */
    private String features;

    /**
     * 失踪时衣着
     */
    private String clothing;

    /**
     * 随身物品
     */
    private String belongings;

    /**
     * 失踪情形（字典 gf_case_type）
     */
    private String caseType;

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
     * 是否已报警
     */
    private Boolean policeReported;

    /**
     * 受理派出所
     */
    private String policeStation;

    /**
     * 警方立案编号
     */
    private String caseAcceptedNo;

    /**
     * 血样采集情况（NOT_COLLECTED/COLLECTED/UNKNOWN）
     */
    private String bloodSampleStatus;

    /**
     * 跟进志愿者
     */
    private String followVolunteer;

    /**
     * 联络方式（IN_APP/PHONE）
     */
    private String contactMode;

    /**
     * 可公开联系电话（加密存储，仅完整视图返回）
     */
    @FieldEncrypt
    @TableField(insertStrategy = FieldStrategy.NOT_EMPTY, updateStrategy = FieldStrategy.NOT_EMPTY)
    private String contactPhone;

    /**
     * 找回时间
     */
    private LocalDateTime foundTime;

    /**
     * 找回确认依据（POLICE/GUARDIAN/DNA）
     */
    private String foundConfirmType;

    /**
     * 找回说明
     */
    private String foundRemark;

    /**
     * 最近一次审核意见
     */
    private String auditRemark;

    /**
     * 发布时间
     */
    private LocalDateTime publishTime;

    /**
     * 最后确认时间（时效计算基准）
     */
    private LocalDateTime lastConfirmTime;

    /**
     * 归档时间
     */
    private LocalDateTime archivedTime;

    /**
     * 当前版本号
     */
    private Integer version;

    /**
     * 来源（APP/COLLECT/IMPORT）
     */
    private String sourceType;

    /**
     * 采集源标识
     */
    private String sourceCode;

    /**
     * 采集源侧编号
     */
    private String sourceId;

    /**
     * 来源页面链接（可追溯）
     */
    private String sourceUrl;

    /**
     * 来源抓取时间
     */
    private LocalDateTime fetchTime;

    /**
     * 去重指纹
     */
    private String dedupeKey;

    /**
     * 合规告知版本号
     */
    private String agreementVersion;

    /**
     * 是否已勾选合规告知（真实承诺与权利人同意）
     */
    private Boolean agreementAccepted;

    /**
     * 合规告知确认时间
     */
    private LocalDateTime agreementTime;

    /**
     * 线索数（冗余）
     */
    private Integer leadCount;

    /**
     * 浏览数（冗余）
     */
    private Integer viewCount;

    /**
     * 收藏数（冗余）
     */
    private Integer favoriteCount;

    /**
     * 被举报次数（冗余）
     */
    private Integer reportCount;
}
