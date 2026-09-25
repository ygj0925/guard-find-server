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

/**
 * 寻人档案版本快照实体（每次提交审核留存全量快照，支撑"信息被改过"的可追溯性）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@TableName("gf_case_version")
public class CaseVersionDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 档案 ID
     */
    private Long caseId;

    /**
     * 版本序号
     */
    private Integer seq;

    /**
     * 提交时全量快照（JSON）
     */
    private String snapshotJson;

    /**
     * 提交/变更说明
     */
    private String reason;

    /**
     * 操作人
     */
    private Long operatorId;

    /**
     * 操作人类型（APP/SYS）
     */
    private String operatorType;
}
