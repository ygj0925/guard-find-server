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
import java.time.LocalDateTime;

/**
 * App 站内消息实体
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@TableName("gf_message")
public class AppMessageDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 接收人（App 用户 ID）
     */
    private Long receiverId;

    /**
     * 事件类型（NotificationEventEnum）
     */
    private String type;

    /**
     * 分组（AUDIT/LEAD/RESULT/SYSTEM）
     */
    private String category;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 业务类型（FIND_CASE/LEAD/REPORT/COLLECT）
     */
    private String bizType;

    /**
     * 业务 ID
     */
    private Long bizId;

    /**
     * 跳转路径
     */
    private String path;

    /**
     * 是否已读
     */
    private Boolean readStatus;

    /**
     * 阅读时间
     */
    private LocalDateTime readTime;
}
