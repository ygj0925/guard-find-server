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

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.DesensitizedUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.entity.SmsResponse;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.notification.enums.NotificationEventEnum;
import com.guardfind.server.notification.service.NotificationSmsService;
import com.guardfind.server.notification.support.NotificationConstants;
import com.guardfind.server.system.model.entity.SmsConfigDO;
import com.guardfind.server.system.service.SmsConfigService;
import top.continew.starter.cache.redisson.util.RedisUtils;
import top.continew.starter.core.constant.StringConstants;

import java.time.Duration;
import java.util.LinkedHashMap;

/**
 * 通知短信业务实现（复用 SMS4J 通道，模板与限流独立于验证码）
 *
 * <p>
 * 可用配置项（均有默认值，未配置时不影响启动）：
 * <ul>
 * <li>{@code guardfind.notification.sms.template-id}：通知类短信模板 ID，留空则使用通道默认模板</li>
 * <li>{@code guardfind.notification.sms.max-per-day}：单用户单日提醒条数上限，{@code <=0} 表示不限流</li>
 * </ul>
 * 模板参数约定为 {@code ${content}}（已由本类裁剪到长度上限），与验证码模板的 {@code code/time} 参数互不干扰。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSmsServiceImpl implements NotificationSmsService {

    /**
     * 短信正文最大长度（超出截断，避免模板变量超长被运营商拒绝）
     */
    private static final int MAX_CONTENT_LENGTH = 50;

    private final AppUserMapper appUserMapper;
    private final SmsConfigService smsConfigService;

    @Value("${guardfind.notification.sms.template-id:}")
    private String templateId;

    @Value("${guardfind.notification.sms.max-per-day:5}")
    private int maxPerDay;

    @Override
    public void sendNoticeSms(Long userId, NotificationEventEnum event, String content) {
        try {
            this.doSendNoticeSms(userId, event, content);
        } catch (Exception e) {
            // 通知短信属尽力而为的增强通道，任何失败都不得回传调用方（站内信已落库）
            log.error("通知短信发送失败（不影响站内信与业务事务），userId={}，event={}", userId, event, e);
        }
    }

    /**
     * 执行短信发送（抽取为独立方法，便于单测隔离短信通道）
     *
     * @param userId  接收人
     * @param event   事件
     * @param content 正文
     */
    void doSendNoticeSms(Long userId, NotificationEventEnum event, String content) {
        AppUserDO user = appUserMapper.selectById(userId);
        String phone = user == null ? null : StrUtil.trim(user.getPhone());
        if (StrUtil.isBlank(phone)) {
            log.warn("用户未绑定手机号，跳过通知短信，userId={}", userId);
            return;
        }
        if (!this.tryAcquireQuota(userId)) {
            log.warn("通知短信已达单日上限，本次跳过，userId={}，上限={}", userId, maxPerDay);
            return;
        }
        SmsBlend smsBlend = this.resolveSmsBlend();
        LinkedHashMap<String, String> messageMap = new LinkedHashMap<>(2);
        messageMap.put("content", StrUtil.subPre(StrUtil.blankToDefault(content, event == null
            ? ""
            : event.getLabel()), MAX_CONTENT_LENGTH));
        SmsResponse smsResponse = StrUtil.isBlank(templateId)
            ? smsBlend.sendMessage(phone, messageMap)
            : smsBlend.sendMessage(phone, StrUtil.trim(templateId), messageMap);
        if (smsResponse == null || !smsResponse.isSuccess()) {
            log.warn("通知短信通道返回失败，userId={}，phone={}，响应={}", userId, DesensitizedUtil.mobilePhone(phone), smsResponse);
        }
    }

    /**
     * 单用户单日限流（Redis 计数，键按日期滚动，与验证码限流键前缀分离）
     *
     * @param userId 接收人
     * @return true：未超限
     */
    boolean tryAcquireQuota(Long userId) {
        if (maxPerDay <= 0) {
            return true;
        }
        String key = NotificationConstants.SMS_LIMIT_KEY_PREFIX + DateUtil.today() + StringConstants.COLON + userId;
        long count = RedisUtils.incr(key);
        if (count == 1L) {
            RedisUtils.expire(key, Duration.ofDays(1));
        }
        return count <= maxPerDay;
    }

    /**
     * 解析短信通道（与后台短信配置保持一致：优先默认配置，无配置时取 SMS4J 全局通道）
     *
     * @return 短信通道
     */
    SmsBlend resolveSmsBlend() {
        SmsConfigDO smsConfig = smsConfigService.getDefaultConfig();
        return smsConfig == null ? SmsFactory.getSmsBlend() : SmsFactory.getSmsBlend(String.valueOf(smsConfig.getId()));
    }
}
