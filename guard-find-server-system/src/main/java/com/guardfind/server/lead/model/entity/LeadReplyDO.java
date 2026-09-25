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

import com.baomidou.mybatisplus.annotation.TableName;
import com.guardfind.server.common.base.model.entity.BaseDO;
import com.guardfind.server.lead.enums.LeadReplyFromEnum;
import lombok.Data;

import java.io.Serial;

/**
 * 线索回复实体
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@TableName("gf_lead_reply")
public class LeadReplyDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 线索 ID
     */
    private Long leadId;

    /**
     * 发起方（OWNER/SUBMITTER）
     */
    private LeadReplyFromEnum fromSide;

    /**
     * 发起人（App 用户 ID）
     */
    private Long fromUserId;

    /**
     * 回复内容
     */
    private String content;
}
