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

package com.guardfind.server.notification.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.guardfind.server.common.base.model.entity.BaseDO;
import lombok.Data;

import java.io.Serial;

/**
 * App 通知偏好实体
 *
 * <p>
 * 仅存"用户显式表达过的"开关：一个 (user_id, type) 至多一行（uk_user_type），无行即代表使用默认值
 * （站内信开、短信关），避免为 9 类事件 × N 用户预生成脏数据。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@TableName("gf_notification_preference")
public class PreferenceDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * App 用户 ID
     */
    private Long userId;

    /**
     * 事件类型（NotificationEventEnum）
     */
    private String type;

    /**
     * 是否开启站内信
     */
    private Boolean inboxEnabled;

    /**
     * 是否开启短信提醒（默认关闭，仅显式开启后发送）
     */
    private Boolean smsEnabled;
}
