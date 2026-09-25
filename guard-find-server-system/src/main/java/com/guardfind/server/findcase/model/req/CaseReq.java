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

package com.guardfind.server.findcase.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 寻人档案创建或修改请求参数
 *
 * <p>
 * 草稿（{@code submit=false}）允许关键字段缺失，仅姓名必填；提交审核时的完整性校验集中在
 * {@code CaseSubmitValidator}，避免两处口径不一致。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "寻人档案创建或修改请求参数")
public class CaseReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 是否直接提交审核（true：进入待审核；false：存草稿）
     */
    @Schema(description = "是否直接提交审核", example = "false")
    private Boolean submit = false;

    /**
     * 与本人关系（字典 gf_relation_type）
     */
    @Schema(description = "与本人关系（字典 gf_relation_type）", example = "PARENT")
    @Length(max = 16, message = "与本人关系长度不能超过 {max} 个字符")
    private String relationType;

    /**
     * 姓名
     */
    @Schema(description = "姓名", example = "张三")
    @NotBlank(message = "姓名不能为空")
    @Length(max = 64, message = "姓名长度不能超过 {max} 个字符")
    private String name;

    /**
     * 性别（UNKNOWN/MALE/FEMALE）
     */
    @Schema(description = "性别（UNKNOWN/MALE/FEMALE）", example = "MALE")
    @Pattern(regexp = "UNKNOWN|MALE|FEMALE", message = "性别取值无效")
    private String gender;

    /**
     * 出生日期
     */
    @Schema(description = "出生日期", example = "2018-05-01", type = "string")
    private LocalDate birthday;

    /**
     * 失踪时年龄
     */
    @Schema(description = "失踪时年龄", example = "6")
    @Min(value = 0, message = "失踪时年龄不能小于 {value}")
    @Max(value = 130, message = "失踪时年龄不能大于 {value}")
    private Integer ageAtMissing;

    /**
     * 别名（小名、口音叫法等）
     */
    @Schema(description = "别名（小名、口音叫法等）")
    @Valid
    @Size(max = 10, message = "别名数量不能超过 {max} 个")
    private List<CaseAliasReq> aliases;

    /**
     * 失踪时间
     */
    @Schema(description = "失踪时间", example = "2024-08-01 14:00:00", type = "string")
    private LocalDateTime missingTime;

    /**
     * 失踪省编码
     */
    @Schema(description = "失踪省编码", example = "130000")
    @Length(max = 12, message = "失踪省编码长度不能超过 {max} 个字符")
    private String provinceCode;

    /**
     * 失踪市编码
     */
    @Schema(description = "失踪市编码", example = "130400")
    @Length(max = 12, message = "失踪市编码长度不能超过 {max} 个字符")
    private String cityCode;

    /**
     * 失踪区县编码
     */
    @Schema(description = "失踪区县编码", example = "130431")
    @Length(max = 12, message = "失踪区县编码长度不能超过 {max} 个字符")
    private String districtCode;

    /**
     * 详细地址（仅完整视图返回）
     */
    @Schema(description = "详细地址（仅完整视图返回）", example = "曲周镇凤城路东段")
    @Length(max = 500, message = "详细地址长度不能超过 {max} 个字符")
    private String address;

    /**
     * 户籍地区划编码
     */
    @Schema(description = "户籍地区划编码", example = "130400")
    @Length(max = 12, message = "户籍地区划编码长度不能超过 {max} 个字符")
    private String hometownCode;

    /**
     * 经度（一期不采集，预留）
     */
    @Schema(description = "经度（一期不采集，预留）", example = "114.9417")
    private BigDecimal lng;

    /**
     * 纬度（一期不采集，预留）
     */
    @Schema(description = "纬度（一期不采集，预留）", example = "36.7682")
    private BigDecimal lat;

    /**
     * 失踪时身高（cm）
     */
    @Schema(description = "失踪时身高（cm）", example = "118")
    @Min(value = 30, message = "失踪时身高不能小于 {value}")
    @Max(value = 250, message = "失踪时身高不能大于 {value}")
    private Integer height;

    /**
     * 体型（字典 gf_body_type）
     */
    @Schema(description = "体型（字典 gf_body_type）", example = "THIN")
    @Length(max = 16, message = "体型长度不能超过 {max} 个字符")
    private String bodyType;

    /**
     * 发型
     */
    @Schema(description = "发型", example = "SHORT")
    @Length(max = 16, message = "发型长度不能超过 {max} 个字符")
    private String hairType;

    /**
     * 口音
     */
    @Schema(description = "口音", example = "邯郸口音")
    @Length(max = 100, message = "口音长度不能超过 {max} 个字符")
    private String accent;

    /**
     * 疤痕/胎记等体貌特征
     */
    @Schema(description = "疤痕/胎记等体貌特征", example = "左眉骨有 2cm 旧疤")
    @Length(max = 500, message = "体貌特征长度不能超过 {max} 个字符")
    private String features;

    /**
     * 失踪时衣着
     */
    @Schema(description = "失踪时衣着", example = "红色羽绒服")
    @Length(max = 255, message = "衣着长度不能超过 {max} 个字符")
    private String clothing;

    /**
     * 随身物品
     */
    @Schema(description = "随身物品", example = "蓝色书包")
    @Length(max = 255, message = "随身物品长度不能超过 {max} 个字符")
    private String belongings;

    /**
     * 失踪情形（字典 gf_case_type）
     */
    @Schema(description = "失踪情形（字典 gf_case_type）", example = "MISSING")
    @Length(max = 20, message = "失踪情形长度不能超过 {max} 个字符")
    private String caseType;

    /**
     * 发生经过
     */
    @Schema(description = "发生经过", example = "当天下午在村口玩耍后失联")
    @Length(max = 2000, message = "发生经过长度不能超过 {max} 个字符")
    private String narrative;

    /**
     * 家庭背景及线索资料
     */
    @Schema(description = "家庭背景及线索资料", example = "家中务农，父母常年在外")
    @Length(max = 2000, message = "家庭背景及线索资料长度不能超过 {max} 个字符")
    private String familyClues;

    /**
     * 其他说明
     */
    @Schema(description = "其他说明", example = "对花粉过敏，需长期服药")
    @Length(max = 1000, message = "其他说明长度不能超过 {max} 个字符")
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
    @Length(max = 100, message = "受理派出所长度不能超过 {max} 个字符")
    private String policeStation;

    /**
     * 警方立案编号
     */
    @Schema(description = "警方立案编号", example = "冀A123456")
    @Length(max = 64, message = "警方立案编号长度不能超过 {max} 个字符")
    private String caseNo;

    /**
     * 血样采集情况（字典 gf_blood_sample_status）
     */
    @Schema(description = "血样采集情况（NOT_COLLECTED/COLLECTED/UNKNOWN）", example = "COLLECTED")
    @Length(max = 20, message = "血样采集情况长度不能超过 {max} 个字符")
    private String bloodSampleStatus;

    /**
     * 跟进志愿者
     */
    @Schema(description = "跟进志愿者", example = "李四")
    @Length(max = 64, message = "跟进志愿者长度不能超过 {max} 个字符")
    private String followVolunteer;

    /**
     * 联络方式（IN_APP/PHONE）
     */
    @Schema(description = "联络方式（IN_APP/PHONE）", example = "IN_APP")
    @Pattern(regexp = "IN_APP|PHONE", message = "联络方式取值无效")
    private String contactMode;

    /**
     * 可公开联系电话（contactMode=PHONE 时必填，仅完整视图返回）
     */
    @Schema(description = "可公开联系电话（仅完整视图返回）", example = "13800001234")
    @Length(max = 20, message = "联系电话长度不能超过 {max} 个字符")
    private String contactPhone;

    /**
     * 影像资料（图片上限 9 张）
     */
    @Schema(description = "影像资料")
    @Valid
    @Size(max = 9, message = "图片数量不能超过 {max} 张")
    private List<CaseMediaReq> mediaUrls;

    /**
     * 合规告知版本号
     */
    @Schema(description = "合规告知版本号", example = "v1")
    @Length(max = 16, message = "合规告知版本号长度不能超过 {max} 个字符")
    private String agreementVersion;

    /**
     * 是否已确认合规告知
     */
    @Schema(description = "是否已确认合规告知", example = "true")
    private Boolean agreementAccepted;

    /**
     * 疑似重复档案时是否强制提交
     */
    @Schema(description = "疑似重复档案时是否强制提交", example = "false")
    private Boolean forceSubmit;
}
