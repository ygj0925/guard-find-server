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

package com.guardfind.server.app.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.guardfind.server.common.base.model.entity.BaseDO;
import lombok.Data;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * App 设备实体（为二期厂商推送预留 push_token，一期仅登记）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@TableName("gf_app_device")
public class AppDeviceDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * App 用户 ID
     */
    private Long userId;

    /**
     * 平台（IOS/ANDROID/HARMONY/WEB）
     */
    private String platform;

    /**
     * 设备标识
     */
    private String deviceId;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 厂商推送 token（一期不写入）
     */
    private String pushToken;

    /**
     * App 版本
     */
    private String appVersion;

    /**
     * 系统版本
     */
    private String osVersion;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginTime;
}
