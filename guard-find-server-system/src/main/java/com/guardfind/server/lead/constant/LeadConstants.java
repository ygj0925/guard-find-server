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

package com.guardfind.server.lead.constant;

import com.guardfind.server.common.constant.CacheConstants;

/**
 * 线索域常量
 *
 * @author guardfind
 * @since 2026/9/24
 */
public final class LeadConstants {

    /**
     * 站内消息业务类型
     */
    public static final String MESSAGE_BIZ_TYPE = "LEAD";

    /**
     * 目标类型（举报）
     */
    public static final String TARGET_TYPE_LEAD = "LEAD";

    /**
     * 线索描述最小长度
     */
    public static final int MIN_DESCRIPTION_LENGTH = 10;

    /**
     * 线索描述最大长度
     */
    public static final int MAX_DESCRIPTION_LENGTH = 1000;

    /**
     * 线索图片上限
     */
    public static final int MAX_MEDIA_COUNT = 3;

    /**
     * 线索回复长度上限
     */
    public static final int MAX_REPLY_LENGTH = 500;

    /**
     * 同一用户对同一档案每小时线索上限
     */
    public static final int MAX_PER_USER_CASE_PER_HOUR = 3;

    /**
     * 同一 IP 每小时线索上限
     */
    public static final int MAX_PER_IP_PER_HOUR = 10;

    /**
     * 限频窗口时长（小时）
     */
    public static final int LIMIT_WINDOW_HOURS = 1;

    /**
     * 处置缓存键前缀
     */
    private static final String LIMIT_KEY_PREFIX = "LEAD" + CacheConstants.DELIMITER + "LIMIT" + CacheConstants.DELIMITER;

    /**
     * 同用户同档案小时计数键
     *
     * @param userId 用户 ID
     * @param caseId 档案 ID
     * @return 缓存键
     */
    public static String userCaseLimitKey(Long userId, Long caseId) {
        return LIMIT_KEY_PREFIX + "USER_CASE" + CacheConstants.DELIMITER + userId + CacheConstants.DELIMITER + caseId;
    }

    /**
     * 同 IP 小时计数键
     *
     * @param ip 客户端 IP
     * @return 缓存键
     */
    public static String ipLimitKey(String ip) {
        return LIMIT_KEY_PREFIX + "IP" + CacheConstants.DELIMITER + ip;
    }

    private LeadConstants() {
    }
}
