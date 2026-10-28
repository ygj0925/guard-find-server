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

package com.guardfind.server.post.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.guardfind.server.common.base.model.entity.BaseDO;
import lombok.Data;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 社区动态实体(朋友圈形态)
 *
 * <p>
 * 合规口径:位置是用户主动输入的地点名 + 可选坐标,不申请定位权限;
 * 正文与评论过敏感词审核,命中即拒绝发布。
 * </p>
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@TableName("gf_post")
public class PostDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 发布者用户 ID
     */
    private Long userId;

    /**
     * 正文
     */
    private String content;

    /**
     * 图片 URL JSON 数组(最多 9 张)
     */
    private String images;

    /**
     * 位置名称(可空)
     */
    private String locationName;

    /**
     * 经度(可选)
     */
    private BigDecimal longitude;

    /**
     * 纬度(可选)
     */
    private BigDecimal latitude;

    /**
     * 点赞数
     */
    private Integer likeCount;

    /**
     * 评论数
     */
    private Integer commentCount;

    /**
     * 状态（0：待审；1：已发布；2：已拒绝）
     */
    private Integer status;
}
