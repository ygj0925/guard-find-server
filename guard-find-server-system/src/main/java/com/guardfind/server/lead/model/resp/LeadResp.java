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

package com.guardfind.server.lead.model.resp;

import com.guardfind.server.lead.enums.LeadStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 线索视图
 *
 * <p>
 * 脱敏边界：{@code contactValue} 仅对档案所有人返回明文，其余身份（含提交人本人、志愿者）一律掩码；
 * 提交人昵称恒为掩码；{@code ip} 属调证信息，永不出现在响应中。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "线索视图")
public class LeadResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 线索 ID
     */
    @Schema(description = "线索 ID", example = "1801000000000000001")
    private Long id;

    /**
     * 档案 ID
     */
    @Schema(description = "档案 ID", example = "1790000000000000001")
    private Long caseId;

    /**
     * 档案姓名
     */
    @Schema(description = "档案姓名", example = "张三")
    private String caseName;

    /**
     * 发现时间
     */
    @Schema(description = "发现时间", example = "2026-09-20 18:30:00", type = "string")
    private LocalDateTime occurTime;

    /**
     * 发现地点描述
     */
    @Schema(description = "发现地点描述", example = "曲周县凤凰新区")
    private String occurAddress;

    /**
     * 线索描述
     */
    @Schema(description = "线索描述", example = "在超市见到一名与照片相似的男孩")
    private String description;

    /**
     * 图片地址列表
     */
    @Schema(description = "图片地址列表")
    private List<String> mediaUrls = new ArrayList<>();

    /**
     * 联络方式（IN_APP/PHONE）
     */
    @Schema(description = "联络方式（IN_APP/PHONE）", example = "IN_APP")
    private String contactMode;

    /**
     * 联系方式（仅档案所有人可见明文，其余身份掩码后返回）
     */
    @Schema(description = "联系方式（仅档案所有人可见明文）", example = "138****1234")
    private String contactValue;

    /**
     * 提交人昵称（掩码）
     */
    @Schema(description = "提交人昵称（掩码）", example = "无脚星友48**")
    private String submitterMasked;

    /**
     * 状态
     */
    @Schema(description = "状态（PENDING/ACCEPTED/REJECTED/CONTACTED）", example = "PENDING")
    private LeadStatusEnum status;

    /**
     * 是否高风险（命中诈骗话术，家属端展示为"待核实"）
     */
    @Schema(description = "是否高风险（命中诈骗话术）", example = "false")
    private Boolean highRisk;

    /**
     * 处置说明
     */
    @Schema(description = "处置说明", example = "已转交属地志愿者核实")
    private String handleRemark;

    /**
     * 双向回复（按时间升序）
     */
    @Schema(description = "双向回复（按时间升序）")
    private List<LeadReplyResp> replies = new ArrayList<>();

    /**
     * 提交时间
     */
    @Schema(description = "提交时间", example = "2026-09-24 11:00:00", type = "string")
    private LocalDateTime createTime;
}
