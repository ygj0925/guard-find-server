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
import com.guardfind.server.findcase.enums.CaseStatusEnum;
import lombok.Data;

import java.io.Serial;

/**
 * 寻人档案状态轨迹实体
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@TableName("gf_case_status_log")
public class CaseStatusLogDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 档案 ID
     */
    private Long caseId;

    /**
     * 变更前状态
     */
    private CaseStatusEnum fromStatus;

    /**
     * 变更后状态
     */
    private CaseStatusEnum toStatus;

    /**
     * 理由码（审核模板）
     */
    private String reasonCode;

    /**
     * 理由说明
     */
    private String reason;

    /**
     * 操作人
     */
    private Long operatorId;

    /**
     * 操作人类型（APP/SYS/SYSTEM）
     */
    private String operatorType;
}
