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

package com.guardfind.server.lead.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.guardfind.server.common.base.model.entity.BaseDO;
import com.guardfind.server.lead.enums.LeadStatusEnum;
import lombok.Data;
import top.continew.starter.encrypt.field.annotation.FieldEncrypt;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 寻人线索实体
 *
 * <p>
 * {@code contactValue} 为加密列（仅档案所有人可解密查看明文）；{@code ip} 仅用于执法调证，任何读接口均不返回。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@TableName(value = "gf_case_lead", autoResultMap = true)
public class LeadDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 档案 ID
     */
    private Long caseId;

    /**
     * 提交人（App 用户 ID）
     */
    private Long submitterId;

    /**
     * 发现时间
     */
    private LocalDateTime occurTime;

    /**
     * 发现地点描述
     */
    private String occurAddress;

    /**
     * 线索描述
     */
    private String description;

    /**
     * 图片地址（JSON 数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> mediaUrls;

    /**
     * 联络方式（IN_APP/PHONE）
     */
    private String contactMode;

    /**
     * 联系方式（加密存储）
     */
    @FieldEncrypt
    @TableField(insertStrategy = FieldStrategy.NOT_EMPTY, updateStrategy = FieldStrategy.NOT_EMPTY)
    private String contactValue;

    /**
     * 状态
     */
    private LeadStatusEnum status;

    /**
     * 是否高风险（命中诈骗话术，需人工核实）
     */
    private Boolean highRisk;

    /**
     * 处置说明
     */
    private String handleRemark;

    /**
     * 提交 IP（调证用，不对外返回）
     */
    private String ip;
}
