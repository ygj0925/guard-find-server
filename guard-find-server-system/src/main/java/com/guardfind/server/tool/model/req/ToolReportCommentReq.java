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

package com.guardfind.server.tool.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * 月报评论请求
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@Schema(description = "月报评论请求")
public class ToolReportCommentReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "评论内容")
    @NotBlank(message = "评论内容不能为空")
    @Length(max = 500, message = "评论不能超过 {max} 个字符")
    private String content;

    @Schema(description = "回复的评论 ID(楼中楼,可空)")
    private Long replyToCommentId;
}
