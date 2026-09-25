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

package com.guardfind.server.notification.service;

import com.guardfind.server.notification.enums.NotificationEventEnum;

/**
 * 通知短信服务
 *
 * <p>
 * 与登录验证码通道（{@code captcha.sms.*}）分离：独立模板、独立限流，避免用户开启提醒后挤占验证码额度。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public interface NotificationSmsService {

    /**
     * 按事件补发一条提醒短信
     *
     * <p>
     * 本方法 MUST NOT 向外抛出异常：短信是可选增强通道，通道未配置、限流、运营商失败都只记日志，
     * 不能影响站内信落库与调用方的业务事务。
     * </p>
     *
     * @param userId  接收人（C 端用户 ID）
     * @param event   事件
     * @param content 短信正文（按模板参数裁剪后下发）
     */
    void sendNoticeSms(Long userId, NotificationEventEnum event, String content);
}
