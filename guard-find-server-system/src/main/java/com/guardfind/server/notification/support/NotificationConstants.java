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

package com.guardfind.server.notification.support;

/**
 * C 端通知常量
 *
 * @author guardfind
 * @since 2026/9/24
 */
public final class NotificationConstants {

    /**
     * 实时帧类型：定向站内信
     */
    public static final String FRAME_TYPE_MESSAGE = "message";

    /**
     * 实时帧类型：心跳请求
     */
    public static final String FRAME_TYPE_PING = "ping";

    /**
     * 实时帧类型：心跳响应
     */
    public static final String FRAME_TYPE_PONG = "pong";

    /**
     * 实时通道握手令牌参数名（GET /app/websocket?token=）
     */
    public static final String WS_TOKEN_PARAM = "token";

    /**
     * 握手阶段解析出的 C 端用户 ID（WebSocketSession attributes 键）
     */
    public static final String WS_USER_ID_ATTR = "APP_USER_ID";

    /**
     * 单用户最大并发会话数（多端登录 + 断线重连的余量，超出则拒绝新会话，避免连接泄漏）
     */
    public static final int MAX_SESSIONS_PER_USER = 10;

    /**
     * 偏好项说明：短信通道成本提示（契约 §5 Preference.smsNote）
     */
    public static final String SMS_NOTE = "短信由运营商计费";

    /**
     * 偏好项名称后缀（契约示例 label 为 "新线索提醒"，枚举 label 为 "新线索"）
     */
    public static final String PREFERENCE_LABEL_SUFFIX = "提醒";

    /**
     * 站内消息业务类型：寻人档案
     */
    public static final String BIZ_TYPE_FIND_CASE = "FIND_CASE";

    /**
     * 通知短信限流键前缀（与验证码通道 CAPTCHA:app: 分离，互不影响）
     */
    public static final String SMS_LIMIT_KEY_PREFIX = "NOTIFICATION" + ":" + "SMS" + ":";

    private NotificationConstants() {
    }
}
