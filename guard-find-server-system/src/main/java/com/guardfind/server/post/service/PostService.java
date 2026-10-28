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

package com.guardfind.server.post.service;

import com.guardfind.server.post.model.req.PostCommentReq;
import com.guardfind.server.post.model.req.PostCreateReq;
import com.guardfind.server.post.model.resp.PostCommentResp;
import com.guardfind.server.post.model.resp.PostResp;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.util.List;

/**
 * 社区动态业务接口(C 端)
 *
 * @author guardfind
 * @since 2026/10/29
 */
public interface PostService {

    /**
     * 发布动态(过敏感词审核,命中即拒绝)
     */
    Long create(PostCreateReq req);

    /**
     * /**
     * 动态广场(仅已发布;sort=latest 最新 / hot 热门)
     */
    PageResp<PostResp> page(String sort, PageQuery pageQuery);

    /**
     * /**
     * 我的动态
     */
    PageResp<PostResp> mine(PageQuery pageQuery);

    /**
     * 指定用户的动态(用户主页)
     */
    PageResp<PostResp> userPage(Long targetUserId, PageQuery pageQuery);

    /**
     * 动态详情
     */
    PostResp detail(Long id);

    /**
     * 评论列表(升序)
     */
    List<PostCommentResp> comments(Long postId);

    /**
     * 发表评论(过敏感词)
     */
    Long comment(Long postId, PostCommentReq req);

    /**
     * 点赞/取消点赞(幂等)
     *
     * @return true=已点赞,false=已取消
     */
    boolean toggleLike(Long postId);

    /**
     * 删除动态(仅本人)
     */
    void delete(Long postId);
}
