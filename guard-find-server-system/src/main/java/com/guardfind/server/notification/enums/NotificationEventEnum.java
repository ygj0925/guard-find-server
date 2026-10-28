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

package com.guardfind.server.notification.enums;

/**
 * C 端通知事件
 *
 * <p>
 * 事件到文案的映射集中在此，业务代码只引用枚举，不散落拼接字符串。
 * {@code inboxForced} 为真的事件不允许用户关闭站内信（审核结果必须可达）。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public enum NotificationEventEnum {

    /**
     * 档案审核通过
     */
    CASE_APPROVED("审核通过", "AUDIT", true, false, "/case/%s"),

    /**
     * 档案审核驳回
     */
    CASE_REJECTED("审核驳回", "AUDIT", true, false, "/case/%s"),

    /**
     * 收到新线索
     */
    CASE_LEAD("新线索", "LEAD", false, false, "/case/%s/leads"),

    /**
     * 线索被回复或处置
     */
    LEAD_REPLIED("线索回执", "LEAD", false, false, "/me/leads"),

    /**
     * 关注的档案已找回
     */
    CASE_FOUND("找回喜报", "RESULT", false, false, "/case/%s"),

    /**
     * 档案即将到期归档
     */
    CASE_EXPIRING("时效提醒", "SYSTEM", false, false, "/me/cases"),

    /**
     * 举报处理结果
     */
    REPORT_RESULT("举报处理结果", "SYSTEM", false, false, "/me/messages"),

    /**
     * 采集候选经审批正式发布
     */
    CASE_PUBLISHED("信息已发布", "AUDIT", false, false, "/case/%s"),
    POST_LIKED("动态获赞", "SOCIAL", false, false, "/community/detail?id=%s"),
    POST_COMMENTED("动态收到评论", "SOCIAL", false, false, "/community/detail?id=%s"),
    SOCIAL_FOLLOWED("新粉丝", "SOCIAL", false, false, "/community/user?id=%s"),

    /**
     * 系统消息
     */
    SYSTEM("系统消息", "SYSTEM", true, false, "/me/messages");

    private final String label;
    private final String category;
    private final boolean inboxForced;
    private final boolean smsDefaultOn;
    private final String pathTemplate;

    NotificationEventEnum(String label,
                          String category,
                          boolean inboxForced,
                          boolean smsDefaultOn,
                          String pathTemplate) {
        this.label = label;
        this.category = category;
        this.inboxForced = inboxForced;
        this.smsDefaultOn = smsDefaultOn;
        this.pathTemplate = pathTemplate;
    }

    public String getLabel() {
        return label;
    }

    public String getCategory() {
        return category;
    }

    public boolean isInboxForced() {
        return inboxForced;
    }

    public boolean isSmsDefaultOn() {
        return smsDefaultOn;
    }

    public String getPathTemplate() {
        return pathTemplate;
    }

    /**
     * 构造跳转路径
     *
     * @param bizId 业务 ID
     * @return 路径
     */
    public String buildPath(Object bizId) {
        return bizId == null ? null : String.format(pathTemplate, bizId);
    }
}
