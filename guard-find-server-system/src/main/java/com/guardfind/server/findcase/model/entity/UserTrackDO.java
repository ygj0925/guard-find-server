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
 * 埋点事件实体（曝光、点击、分享，以及"完整档案访问"留痕）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@TableName("gf_user_track")
public class UserTrackDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * App 用户 ID（匿名上报时为空）
     */
    private Long userId;

    /**
     * 事件（EXPOSE/CLICK/SHARE/PUBLISH/VIEW_FULL）
     */
    private String eventType;

    /**
     * 目标类型（CASE/LEAD/USER）
     */
    private String targetType;

    /**
     * 目标 ID
     */
    private Long targetId;

    /**
     * 扩展信息（JSON）
     */
    private String extra;
}
