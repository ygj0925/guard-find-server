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

package com.guardfind.server.tool.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.guardfind.server.common.base.model.entity.BaseDO;
import lombok.Data;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 签到活动实体
 *
 * <p>
 * 防代签设计:动态码(30 秒轮换,种子仅发起人可见)+ 时间窗 + 一人一次 +
 * 地点名校验(发起人设置了地点时,签到人须填写一致)。不申请定位权限,
 * 位置由用户主动填写(与社区动态同口径)。
 * </p>
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@TableName("gf_tool_checkin")
public class ToolCheckinDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 发起人用户 ID
     */
    private Long userId;

    /**
     * 签到主题
     */
    private String title;

    /**
     * 活动地点名(可空;设置后签到者须填写一致)
     */
    private String locationName;

    /**
     * 签到开始时间
     */
    private LocalDateTime startTime;

    /**
     * 签到截止时间
     */
    private LocalDateTime endTime;

    /**
     * 动态码种子(仅发起人可见)
     */
    private String secret;
}
