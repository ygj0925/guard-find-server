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

package com.guardfind.server.tool.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 汇报活动响应(按"我的角色"差异化返回)
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@Schema(description = "汇报活动响应")
public class ToolReportResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "活动 ID")
    private Long id;

    @Schema(description = "汇报标题")
    private String title;

    @Schema(description = "汇报周期(yyyy-MM)")
    private String period;

    @Schema(description = "截止时间")
    private LocalDateTime dueDate;

    @Schema(description = "我的角色(1 发起人 / 2 填报人 / 3 接收人)")
    private Integer myRole;

    @Schema(description = "填报人总数")
    private Long reporterCount;

    @Schema(description = "已提交数")
    private Long submittedCount;

    @Schema(description = "我是否已提交(我是填报人时)")
    private Boolean submittedByMe;

    @Schema(description = "我的月报状态(1 草稿 / 2 已提交 / null 未开始)")
    private Integer myEntryStatus;

    @Schema(description = "填报人提交状态列表(全成员可见)")
    private List<ReporterStatus> reporters;

    /**
     * 填报人提交状态条目
     */
    @Data
    @Schema(description = "填报人提交状态条目")
    public static class ReporterStatus implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "用户 ID")
        private Long userId;

        @Schema(description = "昵称")
        private String nickname;

        @Schema(description = "头像")
        private String avatar;

        @Schema(description = "月报状态(0 未开始 / 1 草稿 / 2 已提交)")
        private Integer entryStatus;

        @Schema(description = "是否迟交(晚于截止时间提交)")
        private Boolean late;
    }
}
