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

package com.guardfind.server.social.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 关注计数响应(用户主页头部)
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@Schema(description = "关注计数响应")
public class SocialCountsResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "关注数")
    private Long followingCount;

    @Schema(description = "粉丝数")
    private Long followerCount;

    @Schema(description = "动态数(已发布)")
    private Long postCount;

    @Schema(description = "当前登录用户是否已关注该用户(查看他人主页时)")
    private Boolean followedByMe;
}
