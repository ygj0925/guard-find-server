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

package com.guardfind.server.findcase.service;

import com.guardfind.server.findcase.enums.CaseOperatorTypeEnum;
import com.guardfind.server.findcase.enums.CaseStatusEnum;
import com.guardfind.server.findcase.model.entity.CaseStatusLogDO;
import com.guardfind.server.findcase.model.entity.FindCaseDO;

/**
 * 寻人档案状态流转业务接口
 *
 * <p>
 * 档案状态变更的唯一漏斗：任何"发布/驳回/找回/归档/下架/删除"都必须经由此方法，以保证
 * {@code gf_case_status_log} 与站内通知与状态变更同事务落库，不会出现"改了状态没人知道"的裂缝。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public interface CaseStatusService {

    /**
     * 变更档案状态（校验合法性 → 更新状态与时效字段 → 写状态轨迹 → 发通知）
     *
     * @param caseId       档案 ID
     * @param target       目标状态
     * @param reasonCode   理由码（审核模板，可为空）
     * @param reason       理由说明（可为空）
     * @param operatorId   操作人 ID（系统操作传 {@code null}）
     * @param operatorType 操作人类型（APP/SYS/SYSTEM）
     * @return 状态轨迹
     */
    CaseStatusLogDO change(Long caseId,
                           CaseStatusEnum target,
                           String reasonCode,
                           String reason,
                           Long operatorId,
                           CaseOperatorTypeEnum operatorType);

    /**
     * 变更档案状态（已持有实体，避免重复查询）
     *
     * @param caseDO       档案实体
     * @param target       目标状态
     * @param reasonCode   理由码
     * @param reason       理由说明
     * @param operatorId   操作人 ID
     * @param operatorType 操作人类型
     * @return 状态轨迹
     */
    CaseStatusLogDO change(FindCaseDO caseDO,
                           CaseStatusEnum target,
                           String reasonCode,
                           String reason,
                           Long operatorId,
                           CaseOperatorTypeEnum operatorType);
}
