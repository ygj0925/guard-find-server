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

/**
 * 月报阅读/填报响应
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@Schema(description = "月报响应")
public class ToolReportEntryResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "活动 ID")
    private Long activityId;

    @Schema(description = "活动标题")
    private String activityTitle;

    @Schema(description = "汇报周期")
    private String period;

    @Schema(description = "填报人用户 ID")
    private Long userId;

    @Schema(description = "填报人昵称")
    private String nickname;

    @Schema(description = "填报人头像")
    private String avatar;

    @Schema(description = "月报正文(草稿仅本人可读;他人读到已提交)")
    private String content;

    @Schema(description = "状态(1 草稿 / 2 已提交)")
    private Integer status;

    @Schema(description = "是否迟交")
    private Boolean late;

    @Schema(description = "提交时间")
    private LocalDateTime submittedAt;

    @Schema(description = "我的角色(决定能否编辑/评论)")
    private Integer myRole;
}
