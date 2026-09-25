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

package com.guardfind.server.collect.model.resp;

import com.guardfind.server.collect.enums.CandidateStatusEnum;
import com.guardfind.server.common.enums.GenderEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采集候选响应参数
 *
 * <p>
 * 审核工作台据此判断"这条能不能对外"，因此必须自带来源可点击链接、抓取时间与原文摘要：
 * 审核人随时可以回到原页面核对，而不是只看到我们改过的字段。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "采集候选响应参数")
public class CandidateResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "候选 ID", example = "1900000000000000001")
    private Long id;

    @Schema(description = "采集源标识", example = "rangaihuijia")
    private String sourceCode;

    @Schema(description = "源侧编号", example = "143036")
    private String sourceId;

    @Schema(description = "来源页面链接", example = "https://www.example.com/xunren/view/143036")
    private String sourceUrl;

    @Schema(description = "抓取时间", example = "2026-09-24 09:00:00", type = "string")
    private LocalDateTime fetchTime;

    @Schema(description = "原文摘要（SHA-256）")
    private String rawSha256;

    @Schema(description = "源侧类别原文", example = "被拐")
    private String categoryText;

    @Schema(description = "姓名", example = "张三合")
    private String name;

    @Schema(description = "别名")
    private List<String> aliases;

    @Schema(description = "性别", example = "MALE")
    private GenderEnum gender;

    @Schema(description = "出生日期", example = "2001-03-12", type = "string")
    private LocalDate birthday;

    @Schema(description = "失踪时年龄", example = "24")
    private Integer ageAtMissing;

    @Schema(description = "失踪时间（日精度）", example = "2004-08-15 00:00:00", type = "string")
    private LocalDateTime missingTime;

    @Schema(description = "失踪省编码", example = "130000")
    private String provinceCode;

    @Schema(description = "失踪市编码", example = "130100")
    private String cityCode;

    @Schema(description = "失踪区县编码", example = "130104")
    private String districtCode;

    @Schema(description = "区划原文", example = "河北省石家庄市")
    private String regionText;

    @Schema(description = "失踪地址原文", example = "桥西区胜利南街 88 号")
    private String address;

    @Schema(description = "失踪时身高（cm）", example = "120")
    private Integer height;

    @Schema(description = "特征描述")
    private String features;

    @Schema(description = "发生经过")
    private String narrative;

    @Schema(description = "家庭背景及线索资料")
    private String familyClues;

    @Schema(description = "其他说明")
    private String otherNote;

    @Schema(description = "源页面公开的寻亲联系人原文")
    private String contactText;

    @Schema(description = "照片地址")
    private String coverUrl;

    @Schema(description = "失踪情形", example = "TRAFFICKING")
    private String caseType;

    @Schema(description = "血样采集情况", example = "UNKNOWN")
    private String bloodSampleStatus;

    @Schema(description = "去重指纹")
    private String dedupeKey;

    @Schema(description = "字段完整度（0-1）", example = "0.62")
    private BigDecimal completeness;

    @Schema(description = "状态", example = "PENDING")
    private CandidateStatusEnum status;

    @Schema(description = "主候选 ID", example = "1900000000000000000")
    private Long primaryId;

    @Schema(description = "审批/合并产生的档案 ID", example = "1900000000000000002")
    private Long caseId;

    @Schema(description = "驳回理由")
    private String rejectReason;

    @Schema(description = "批次号", example = "20260924090012")
    private String batchNo;

    @Schema(description = "站内疑似重复档案 ID（提示合并，不自动合并）")
    private List<Long> similarCaseIds;

    @Schema(description = "创建时间", example = "2026-09-24 09:00:01", type = "string")
    private LocalDateTime createTime;
}
