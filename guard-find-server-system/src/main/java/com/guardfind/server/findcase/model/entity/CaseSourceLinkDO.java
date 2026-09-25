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

import com.baomidou.mybatisplus.annotation.TableName;
import com.guardfind.server.common.base.model.entity.BaseDO;
import lombok.Data;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 寻人档案来源关联实体（记录审批/合并进入正式库的采集来源，支撑来源追溯与批量撤回）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@TableName("gf_case_source_link")
public class CaseSourceLinkDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 档案 ID
     */
    private Long caseId;

    /**
     * 采集候选 ID
     */
    private Long candidateId;

    /**
     * 采集源标识
     */
    private String sourceCode;

    /**
     * 源侧编号
     */
    private String sourceId;

    /**
     * 源页面链接
     */
    private String sourceUrl;

    /**
     * 抓取时间
     */
    private LocalDateTime fetchTime;

    /**
     * 关联方式（APPROVE：审批生成；MERGE：合并入既有档案）
     */
    private String linkType;
}
