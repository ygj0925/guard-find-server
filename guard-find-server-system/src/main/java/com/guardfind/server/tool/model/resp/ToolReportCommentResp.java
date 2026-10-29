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
 * 月报评论响应
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@Schema(description = "月报评论响应")
public class ToolReportCommentResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "评论 ID")
    private Long id;

    @Schema(description = "评论人用户 ID")
    private Long userId;

    @Schema(description = "评论人昵称")
    private String nickname;

    @Schema(description = "评论人头像")
    private String avatar;

    @Schema(description = "评论内容")
    private String content;

    @Schema(description = "回复的评论 ID(可空)")
    private Long replyToCommentId;

    @Schema(description = "回复目标用户 ID(可空)")
    private Long replyToUserId;

    @Schema(description = "评论时间")
    private LocalDateTime createTime;
}
