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

package com.guardfind.server.notification.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardfind.server.notification.enums.NotificationEventEnum;
import com.guardfind.server.notification.mapper.AppMessageMapper;
import com.guardfind.server.notification.mapper.PreferenceMapper;
import com.guardfind.server.notification.model.entity.AppMessageDO;
import com.guardfind.server.notification.model.entity.PreferenceDO;
import com.guardfind.server.notification.service.NotificationRealtimeService;
import com.guardfind.server.notification.service.NotificationSmsService;
import com.guardfind.server.notification.support.PreferenceSupport;
import com.guardfind.server.support.ContainerlessTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 站内消息发送单元测试：先落库后推送，推送/短信失败不外溢
 */
class AppMessageServiceImplTest extends ContainerlessTestBase {

    private final AppMessageMapper messageMapper = mock(AppMessageMapper.class);
    private final NotificationRealtimeService realtime = mock(NotificationRealtimeService.class);
    private final NotificationSmsService sms = mock(NotificationSmsService.class);

    private AppMessageServiceImpl service(PreferenceDO preference) {
        AppMessageServiceImpl service = spy(new AppMessageServiceImpl(messageMapper, mock(PreferenceMapper.class), realtime, sms, new ObjectMapper()
            .findAndRegisterModules()));
        doReturn(preference).when(service).findPreference(any(), any());
        return service;
    }

    private static PreferenceDO preference(Boolean inbox, Boolean sms) {
        PreferenceDO preference = new PreferenceDO();
        preference.setInboxEnabled(inbox);
        preference.setSmsEnabled(sms);
        return preference;
    }

    @Test
    @DisplayName("实时推送异常不外抛，消息已落库")
    void realtimeFailureDoesNotRollback() {
        when(realtime.sendToUser(anyLong(), anyString())).thenThrow(new IllegalStateException("socket closed"));
        assertThatCode(() -> service(null).send(7L, NotificationEventEnum.CASE_LEAD, "CASE", 9L, "新线索", "有人提供了线索"))
            .doesNotThrowAnyException();
        verify(messageMapper, times(1)).insert(any(AppMessageDO.class));
        verify(realtime, times(1)).sendToUser(anyLong(), anyString());
    }

    @Test
    @DisplayName("短信通道异常同样不外抛")
    void smsFailureDoesNotRollback() {
        org.mockito.Mockito.doThrow(new IllegalStateException("sms down")).when(sms).sendNoticeSms(any(), any(), any());
        assertThatCode(() -> service(preference(true, true))
            .send(7L, NotificationEventEnum.CASE_LEAD, "CASE", 9L, null, "内容")).doesNotThrowAnyException();
        verify(messageMapper, times(1)).insert(any(AppMessageDO.class));
        verify(sms, times(1)).sendNoticeSms(any(), any(), any());
    }

    @Test
    @DisplayName("关闭站内信订阅后不落库也不推送")
    void inboxDisabledSkipsInsert() {
        service(preference(false, false)).send(7L, NotificationEventEnum.CASE_LEAD, "CASE", 9L, "t", "c");
        verify(messageMapper, never()).insert(any(AppMessageDO.class));
        verify(realtime, never()).sendToUser(anyLong(), anyString());
        verify(sms, never()).sendNoticeSms(any(), any(), any());
    }

    @Test
    @DisplayName("强制类事件（审核结果）不受偏好关闭影响")
    void forcedEventIgnoresPreference() {
        service(preference(false, false)).send(7L, NotificationEventEnum.CASE_REJECTED, "CASE", 9L, "t", "c");
        verify(messageMapper, times(1)).insert(any(AppMessageDO.class));
        assertThat(PreferenceSupport.isInboxEnabled(NotificationEventEnum.CASE_APPROVED, preference(false, null)))
            .isTrue();
    }

    @Test
    @DisplayName("偏好默认值：站内信开、短信关")
    void preferenceDefaults() {
        assertThat(PreferenceSupport.isInboxEnabled(NotificationEventEnum.CASE_LEAD, null)).isTrue();
        assertThat(PreferenceSupport.isSmsEnabled(null)).isFalse();
        assertThat(PreferenceSupport.resolveEvent(" case_lead ")).isEqualTo(NotificationEventEnum.CASE_LEAD);
        assertThat(PreferenceSupport.resolveEvent("UNKNOWN")).isNull();
    }
}
