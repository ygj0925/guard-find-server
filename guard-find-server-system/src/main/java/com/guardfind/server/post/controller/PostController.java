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

package com.guardfind.server.post.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;
import top.continew.starter.ratelimiter.annotation.RateLimiter;
import top.continew.starter.ratelimiter.enums.LimitType;

import com.guardfind.server.post.model.req.PostCommentReq;
import com.guardfind.server.post.model.req.PostCreateReq;
import com.guardfind.server.post.model.resp.PostCommentResp;
import com.guardfind.server.post.model.resp.PostResp;
import com.guardfind.server.post.service.PostService;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 社区动态 API(朋友圈形态)
 *
 * <p>
 * 合规:发布/评论过敏感词审核;位置为用户主动提供的名称+可选坐标,不申请定位权限;
 * 点赞幂等;删除仅本人。发布/评论限流防刷。
 * </p>
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Tag(name = "社区动态 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/post")
public class PostController {

    private final PostService postService;

    @Operation(summary = "发布动态", description = "正文必填;图片最多 9 张(先经 /file/upload);位置可空;过敏感词审核")
    @RateLimiter(name = "APP:POST:CREATE", rate = 10, interval = 1, unit = TimeUnit.HOURS, type = LimitType.IP, message = "发布太频繁,请稍后再试")
    @PostMapping
    public Long create(@RequestBody @Valid PostCreateReq req) {
        return postService.create(req);
    }

    @Operation(summary = "动态广场", description = "仅已发布,按时间倒序;带作者信息与 likedByMe")
    @GetMapping("/page")
    public PageResp<PostResp> page(@RequestParam(required = false, defaultValue = "latest") String sort,
                                   PageQuery pageQuery) {
        return postService.page(sort, pageQuery);
    }

    @Operation(summary = "指定用户的动态", description = "用户主页用;仅已发布")
    @Parameter(name = "userId", description = "用户 ID", required = true)
    @GetMapping("/user/{userId}")
    public PageResp<PostResp> userPage(@PathVariable Long userId, PageQuery pageQuery) {
        return postService.userPage(userId, pageQuery);
    }

    @Operation(summary = "我的动态")
    @GetMapping("/mine")
    public PageResp<PostResp> mine(PageQuery pageQuery) {
        return postService.mine(pageQuery);
    }

    @Operation(summary = "动态详情")
    @Parameter(name = "id", description = "动态 ID", required = true)
    @GetMapping("/{id}")
    public PostResp detail(@PathVariable Long id) {
        return postService.detail(id);
    }

    @Operation(summary = "评论列表", description = "按时间升序,全量(单条动态评论量级小)")
    @Parameter(name = "id", description = "动态 ID", required = true)
    @GetMapping("/{id}/comment")
    public List<PostCommentResp> comments(@PathVariable Long id) {
        return postService.comments(id);
    }

    @Operation(summary = "发表评论", description = "过敏感词审核")
    @Parameter(name = "id", description = "动态 ID", required = true)
    @RateLimiter(name = "APP:POST:COMMENT", rate = 30, interval = 1, unit = TimeUnit.MINUTES, type = LimitType.IP)
    @PostMapping("/{id}/comment")
    public Long comment(@PathVariable Long id, @RequestBody @Valid PostCommentReq req) {
        return postService.comment(id, req);
    }

    @Operation(summary = "点赞/取消点赞", description = "幂等;返回 true=已点赞")
    @Parameter(name = "id", description = "动态 ID", required = true)
    @PostMapping("/{id}/like")
    public Boolean toggleLike(@PathVariable Long id) {
        return postService.toggleLike(id);
    }

    @Operation(summary = "删除动态", description = "仅本人")
    @Parameter(name = "id", description = "动态 ID", required = true)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        postService.delete(id);
    }
}
