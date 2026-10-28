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

package com.guardfind.server.social.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.guardfind.server.common.base.model.entity.BaseDO;
import lombok.Data;

import java.io.Serial;

/**
 * 社交关注关系实体(follower 关注 following)
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@TableName("gf_social_follow")
public class SocialFollowDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关注者用户 ID
     */
    private Long followerId;

    /**
     * 被关注用户 ID
     */
    private Long followingId;
}
