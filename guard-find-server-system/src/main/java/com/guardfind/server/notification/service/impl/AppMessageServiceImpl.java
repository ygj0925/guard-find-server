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

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardfind.server.notification.converter.MessageConverter;
import com.guardfind.server.notification.enums.NotificationEventEnum;
import com.guardfind.server.notification.mapper.AppMessageMapper;
import com.guardfind.server.notification.mapper.PreferenceMapper;
import com.guardfind.server.notification.model.entity.AppMessageDO;
import com.guardfind.server.notification.model.entity.PreferenceDO;
import com.guardfind.server.notification.model.resp.AppMessageResp;
import com.guardfind.server.notification.service.AppMessageService;
import com.guardfind.server.notification.service.NotificationRealtimeService;
import com.guardfind.server.notification.service.NotificationSmsService;
import com.guardfind.server.notification.support.PreferenceSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.continew.starter.core.util.validation.ValidationUtils;

import java.time.LocalDateTime;

/**
 * App 站内消息业务实现
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppMessageServiceImpl implements AppMessageService {

    /**
     * 标题最大长度（对齐 gf_message.title varchar(64)）
     */
    private static final int MAX_TITLE_LENGTH = 64;

    /**
     * 内容最大长度（对齐 gf_message.content varchar(500)）
     */
    private static final int MAX_CONTENT_LENGTH = 500;

    private final AppMessageMapper messageMapper;
    private final PreferenceMapper preferenceMapper;
    private final NotificationRealtimeService notificationRealtimeService;
    private final NotificationSmsService notificationSmsService;
    /**
     * 项目统一 Jackson 配置（容器注入），保证实时帧与 HTTP 响应字段形态一致
     */
    private final ObjectMapper objectMapper;

    /**
     * 发送站内消息
     *
     * <p>
     * <b>为什么用 {@link Propagation#REQUIRED}：</b>本方法由业务服务（审核、线索、举报处置、临期任务等）在其事务内调用，
     * 通知必须与业务变更同生共死——业务回滚时不应留下一条"已审核通过"的站内信，因此以 REQUIRED 加入调用方事务，
     * 不新开也不挂起。<br>
     * <b>为什么推送要单独 try/catch：</b>实时下发（WebSocket）与短信都是"尽力而为"的旁路，接收方不在线、通道未配置、
     * 会话写失败都只是"少了一次加速"，消息本身已经落库，App 回前台或轮询 {@code /app/message/unread} 时仍可见；
     * 若让这些异常外溢会把调用方的业务事务标为 rollback-only，导致"因为发不出通知而回滚审核结果"的错误语义。
     * </p>
     *
     * <p>
     * 已知取舍：推送发生在事务提交之前，极端情况下 App 收到帧后立即回拉列表可能还读不到该条（事务尚未提交），
     * 契约 §5 的"断线/异常改 30 秒轮询未读数"天然吸收了这个竞态；改成 afterCommit 投递会让无事务上下文的调用点
     * （定时任务）行为不一致，一期不引入。
     * </p>
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void send(Long receiverId,
                     NotificationEventEnum event,
                     String bizType,
                     Long bizId,
                     String title,
                     String content) {
        ValidationUtils.throwIfNull(receiverId, "消息接收人不能为空");
        ValidationUtils.throwIfNull(event, "通知事件不能为空");
        PreferenceDO preference = this.findPreference(receiverId, event);
        // 强制类事件（inboxForced）不允许被偏好关闭：审核结果必须可达
        boolean inboxEnabled = PreferenceSupport.isInboxEnabled(event, preference);
        boolean smsEnabled = PreferenceSupport.isSmsEnabled(preference);
        if (!inboxEnabled) {
            log.debug("用户已关闭该事件站内信，跳过落库，userId={}，event={}", receiverId, event);
            // 站内信关闭但短信显式开启时仍补发短信（两个通道互相独立，见契约 §5 Preference）
            this.pushSms(receiverId, event, title, content, smsEnabled);
            return;
        }
        AppMessageDO message = this.buildMessage(receiverId, event, bizType, bizId, title, content);
        messageMapper.insert(message);
        log.info("站内消息已落库，id={}，receiverId={}，event={}", message.getId(), receiverId, event);
        this.pushRealtime(message);
        this.pushSms(receiverId, event, title, content, smsEnabled);
    }

    @Override
    public long countUnread(Long receiverId) {
        if (receiverId == null) {
            return 0L;
        }
        Long count = messageMapper.selectCount(Wrappers.<AppMessageDO>lambdaQuery()
            .eq(AppMessageDO::getReceiverId, receiverId)
            .eq(AppMessageDO::getReadStatus, false));
        return count == null ? 0L : count;
    }

    /**
     * 构造消息实体
     *
     * @param receiverId 接收人
     * @param event      事件
     * @param bizType    业务类型
     * @param bizId      业务 ID
     * @param title      标题（为空回退事件名称）
     * @param content    内容（为空回退事件名称）
     * @return 消息实体
     */
    AppMessageDO buildMessage(Long receiverId,
                              NotificationEventEnum event,
                              String bizType,
                              Long bizId,
                              String title,
                              String content) {
        AppMessageDO message = new AppMessageDO();
        message.setReceiverId(receiverId);
        message.setType(event.name());
        // category 由事件目录派生，业务侧不再传分组，避免同一事件落到两个分组
        message.setCategory(event.getCategory());
        message.setTitle(StrUtil.subPre(StrUtil.blankToDefault(title, event.getLabel()), MAX_TITLE_LENGTH));
        message.setContent(StrUtil.subPre(StrUtil.blankToDefault(content, event.getLabel()), MAX_CONTENT_LENGTH));
        message.setBizType(StrUtil.trimToNull(bizType));
        message.setBizId(bizId);
        message.setPath(event.buildPath(bizId));
        message.setReadStatus(false);
        // 系统代发：create_user 交由元对象处理器按当前操作人填充（后台审核动作会记审核人，定时任务记空）
        message.setCreateTime(LocalDateTime.now());
        return message;
    }

    /**
     * 实时下发（异常只记日志，绝不外抛，见类注释）
     *
     * @param message 消息实体
     */
    void pushRealtime(AppMessageDO message) {
        try {
            AppMessageResp resp = MessageConverter.toResp(message);
            int delivered = notificationRealtimeService.sendToUser(message.getReceiverId(), MessageConverter
                .buildMessageFrameJson(objectMapper, resp));
            if (delivered == 0) {
                log.debug("接收方不在线，仅落库，receiverId={}", message.getReceiverId());
            }
        } catch (Exception e) {
            log.error("站内消息实时下发失败（消息已落库，不影响业务事务），id={}，receiverId={}：{}", message.getId(), message.getReceiverId(), e
                .getMessage(), e);
        }
    }

    /**
     * 短信补发（仅在用户显式开启该事件短信订阅时执行；异常只记日志）
     *
     * @param receiverId 接收人
     * @param event      事件
     * @param title      标题
     * @param content    内容
     * @param smsEnabled 是否已显式开启短信
     */
    void pushSms(Long receiverId, NotificationEventEnum event, String title, String content, boolean smsEnabled) {
        if (!smsEnabled) {
            return;
        }
        try {
            notificationSmsService.sendNoticeSms(receiverId, event, StrUtil.blankToDefault(content, title));
        } catch (Exception e) {
            log.error("通知短信补发失败（不影响业务事务），receiverId={}，event={}：{}", receiverId, event, e.getMessage(), e);
        }
    }

    /**
     * 查询用户对某事件的偏好（无记录返回 null，由 {@link PreferenceSupport} 套默认值）
     *
     * @param receiverId 接收人
     * @param event      事件
     * @return 偏好记录，未配置时为 null
     */
    PreferenceDO findPreference(Long receiverId, NotificationEventEnum event) {
        return preferenceMapper.selectOne(Wrappers.<PreferenceDO>lambdaQuery()
            .eq(PreferenceDO::getUserId, receiverId)
            .eq(PreferenceDO::getType, event.name())
            .last("LIMIT 1"));
    }
}
