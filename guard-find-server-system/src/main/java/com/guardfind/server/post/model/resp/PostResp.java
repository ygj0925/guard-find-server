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

package com.guardfind.server.post.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 动态响应
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@Schema(description = "动态响应")
public class PostResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "动态 ID")
    private Long id;

    @Schema(description = "发布者用户 ID")
    private Long userId;

    @Schema(description = "发布者昵称")
    private String nickname;

    @Schema(description = "发布者头像")
    private String avatar;

    @Schema(description = "正文")
    private String content;

    @Schema(description = "图片 URL 列表")
    private List<String> images;

    @Schema(description = "位置名称(可空)")
    private String locationName;

    @Schema(description = "经度(可空)")
    private BigDecimal longitude;

    @Schema(description = "纬度(可空)")
    private BigDecimal latitude;

    @Schema(description = "点赞数")
    private Integer likeCount;

    @Schema(description = "评论数")
    private Integer commentCount;

    @Schema(description = "当前用户是否已点赞")
    private Boolean likedByMe;

    @Schema(description = "发布时间")
    private LocalDateTime createTime;
}
