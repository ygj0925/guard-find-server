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
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 寻人档案完整视图（公开视图 + 敏感字段 + 状态轨迹）
 *
 * <p>
 * 仅返回给档案所有人、已认证志愿者与审核账号；每次返回都会写入一条访问留痕。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "寻人档案完整视图")
public class CaseDetailResp extends CasePublicResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 详细地址
     */
    @Schema(description = "详细地址", example = "曲周镇凤城路东段")
    private String address;

    /**
     * 失踪时间（精确到秒）
     */
    @Schema(description = "失踪时间", example = "2024-08-01 14:00:00", type = "string")
    private LocalDateTime missingTime;

    /**
     * 出生日期
     */
    @Schema(description = "出生日期", example = "2018-05-01", type = "string")
    private LocalDate birthday;

    /**
     * 户籍地文本
     */
    @Schema(description = "户籍地文本", example = "河北省邯郸市")
    private String hometownText;

    /**
     * 体型
     */
    @Schema(description = "体型", example = "THIN")
    private String bodyType;

    /**
     * 发型
     */
    @Schema(description = "发型", example = "SHORT")
    private String hairType;

    /**
     * 口音
     */
    @Schema(description = "口音", example = "邯郸口音")
    private String accent;

    /**
     * 失踪时衣着
     */
    @Schema(description = "失踪时衣着", example = "红色羽绒服")
    private String clothing;

    /**
     * 随身物品
     */
    @Schema(description = "随身物品", example = "蓝色书包")
    private String belongings;

    /**
     * 发生经过
     */
    @Schema(description = "发生经过", example = "当天下午在村口玩耍后失联")
    private String narrative;

    /**
     * 家庭背景及线索资料
     */
    @Schema(description = "家庭背景及线索资料", example = "家中务农，父母常年在外")
    private String familyClues;

    /**
     * 其他说明
     */
    @Schema(description = "其他说明", example = "对花粉过敏，需长期服药")
    private String otherNote;

    /**
     * 是否已报警
     */
    @Schema(description = "是否已报警", example = "true")
    private Boolean policeReported;

    /**
     * 受理派出所
     */
    @Schema(description = "受理派出所", example = "曲周派出所")
    private String policeStation;

    /**
     * 警方立案编号
     */
    @Schema(description = "警方立案编号", example = "冀A123456")
    private String caseNo;

    /**
     * 血样采集情况
     */
    @Schema(description = "血样采集情况", example = "COLLECTED")
    private String bloodSampleStatus;

    /**
     * 跟进志愿者
     */
    @Schema(description = "跟进志愿者", example = "李四")
    private String followVolunteer;

    /**
     * 联络方式（IN_APP/PHONE）
     */
    @Schema(description = "联络方式（IN_APP/PHONE）", example = "PHONE")
    private String contactMode;

    /**
     * 可公开联系电话
     */
    @Schema(description = "可公开联系电话", example = "13800001234")
    private String contactPhone;

    /**
     * 与本人关系（字典 gf_relation_type）
     */
    @Schema(description = "与本人关系（字典 gf_relation_type）", example = "PARENT")
    private String relationType;

    /**
     * 当前版本号
     */
    @Schema(description = "当前版本号", example = "2")
    private Integer version;

    /**
     * 最近一次审核意见
     */
    @Schema(description = "最近一次审核意见", example = "照片不清晰，请补充近照")
    private String auditRemark;

    /**
     * 找回时间
     */
    @Schema(description = "找回时间", example = "2026-09-24 10:00:00", type = "string")
    private LocalDateTime foundTime;

    /**
     * 状态轨迹
     */
    @Schema(description = "状态轨迹")
    private List<CaseStatusHistoryResp> statusHistory;
}
