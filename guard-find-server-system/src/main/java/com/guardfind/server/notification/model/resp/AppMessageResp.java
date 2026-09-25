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

package com.guardfind.server.notification.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内消息响应参数（契约 §5 Message）
 *
 * <p>
 * 字段与契约逐字一致：{@code readStatus} 为布尔、{@code path} 为服务端按事件模板拼好的跳转路径，
 * App 直接按该路径跳转，不再自行拼接路由。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "站内消息响应参数")
public class AppMessageResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 消息 ID
     */
    @Schema(description = "消息 ID", example = "1810000000000000001")
    private Long id;

    /**
     * 事件类型
     */
    @Schema(description = "事件类型（CASE_APPROVED/CASE_REJECTED/CASE_LEAD/LEAD_REPLIED/CASE_FOUND/CASE_EXPIRING/REPORT_RESULT/CASE_PUBLISHED/SYSTEM）", example = "CASE_LEAD")
    private String type;

    /**
     * 标题
     */
    @Schema(description = "标题", example = "收到 1 条新线索")
    private String title;

    /**
     * 内容
     */
    @Schema(description = "内容", example = "张三(男/6岁)收到来自好心人的线索")
    private String content;

    /**
     * 业务类型
     */
    @Schema(description = "业务类型（FIND_CASE/LEAD/REPORT/COLLECT）", example = "FIND_CASE")
    private String bizType;

    /**
     * 业务 ID
     */
    @Schema(description = "业务 ID", example = "1790000000000000001")
    private Long bizId;

    /**
     * 跳转路径
     */
    @Schema(description = "跳转路径", example = "/case/1790000000000000001/leads")
    private String path;

    /**
     * 是否已读
     */
    @Schema(description = "是否已读", example = "false")
    private Boolean readStatus;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", example = "2026-09-24 11:00:05", type = "string")
    private LocalDateTime createTime;
}
