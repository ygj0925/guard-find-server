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

import cn.hutool.core.util.StrUtil;
import com.guardfind.server.notification.enums.NotificationEventEnum;
import com.guardfind.server.notification.model.entity.PreferenceDO;
import com.guardfind.server.notification.model.resp.PreferenceResp;

import java.util.Arrays;
import java.util.List;

/**
 * 通知偏好判定与视图装配
 *
 * <p>
 * 默认值只在这里定义一次（站内信开、短信关），写入侧（偏好设置）与读取侧（消息发送）共用，避免两侧默认值漂移。
 * 强制类事件（{@code inboxForced}）无论库中存什么都视为开启，站内信是审核结果的唯一必达通道。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public final class PreferenceSupport {

    private PreferenceSupport() {
    }

    /**
     * 事件目录（偏好界面按此全量呈现，含 SYSTEM）
     *
     * @return 事件列表（枚举声明顺序）
     */
    public static List<NotificationEventEnum> catalog() {
        return Arrays.asList(NotificationEventEnum.values());
    }

    /**
     * 解析事件类型
     *
     * @param type 事件类型
     * @return 事件枚举，不合法时为 null
     */
    public static NotificationEventEnum resolveEvent(String type) {
        if (StrUtil.isBlank(type)) {
            return null;
        }
        return Arrays.stream(NotificationEventEnum.values())
            .filter(event -> event.name().equalsIgnoreCase(StrUtil.trim(type)))
            .findFirst()
            .orElse(null);
    }

    /**
     * 站内信是否开启：无偏好记录按默认开启；强制类事件恒为开启
     *
     * @param event      事件
     * @param preference 偏好记录（可为 null）
     * @return true：需要落库并实时下发
     */
    public static boolean isInboxEnabled(NotificationEventEnum event, PreferenceDO preference) {
        if (event != null && event.isInboxForced()) {
            return true;
        }
        return preference == null || !Boolean.FALSE.equals(preference.getInboxEnabled());
    }

    /**
     * 短信是否开启：仅在用户显式开启后发送（默认关闭）
     *
     * @param preference 偏好记录（可为 null）
     * @return true：需要补发一条提醒短信
     */
    public static boolean isSmsEnabled(PreferenceDO preference) {
        return preference != null && Boolean.TRUE.equals(preference.getSmsEnabled());
    }

    /**
     * 转换为响应参数（库中无记录时回显默认值）
     *
     * @param event      事件
     * @param preference 偏好记录（可为 null）
     * @return 响应参数
     */
    public static PreferenceResp toResp(NotificationEventEnum event, PreferenceDO preference) {
        PreferenceResp resp = new PreferenceResp();
        resp.setType(event.name());
        resp.setLabel(event.getLabel() + NotificationConstants.PREFERENCE_LABEL_SUFFIX);
        resp.setInboxForced(event.isInboxForced());
        resp.setInboxEnabled(isInboxEnabled(event, preference));
        resp.setSmsEnabled(isSmsEnabled(preference));
        resp.setSmsNote(NotificationConstants.SMS_NOTE);
        return resp;
    }
}
