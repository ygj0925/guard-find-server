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

package com.guardfind.server.moderation.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.guardfind.server.common.base.model.entity.BaseDO;
import com.guardfind.server.moderation.enums.PunishmentTypeEnum;
import lombok.Data;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 用户处置记录实体
 *
 * <p>
 * 一期仅登记处置事实（供客服与申诉核对），登录拦截由账号域读取本表判定，处置记录本身不直接改动账号状态。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@TableName("gf_user_punishment")
public class UserPunishmentDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 被处置用户（App 用户 ID）
     */
    private Long userId;

    /**
     * 处置类型（MUTE/BAN）
     */
    private PunishmentTypeEnum type;

    /**
     * 时长（天，空为永久）
     */
    private Integer days;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 原因
     */
    private String reason;

    /**
     * 关联举报 ID
     */
    private Long reportId;
}
