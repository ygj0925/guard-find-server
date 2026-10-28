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

package com.guardfind.server.social.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;
import top.continew.starter.ratelimiter.annotation.RateLimiter;
import top.continew.starter.ratelimiter.enums.LimitType;

import com.guardfind.server.social.model.resp.SocialCountsResp;
import com.guardfind.server.social.model.resp.SocialUserBriefResp;
import com.guardfind.server.social.service.SocialFollowService;

import java.util.concurrent.TimeUnit;

/**
 * 社交关注 API(掘金式:关注/粉丝/计数)
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Tag(name = "社交关注 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/social")
public class SocialFollowController {

    private final SocialFollowService followService;

    @Operation(summary = "关注", description = "幂等;不能关注自己;对方收到新粉丝站内信;返回 true=本次新关注")
    @Parameter(name = "userId", description = "目标用户 ID", required = true)
    @RateLimiter(name = "APP:SOCIAL:FOLLOW", rate = 30, interval = 1, unit = TimeUnit.MINUTES, type = LimitType.IP)
    @PostMapping("/follow/{userId}")
    public Boolean follow(@PathVariable Long userId) {
        return followService.follow(userId);
    }

    @Operation(summary = "取消关注", description = "幂等")
    @Parameter(name = "userId", description = "目标用户 ID", required = true)
    @PostMapping("/unfollow/{userId}")
    public void unfollow(@PathVariable Long userId) {
        followService.unfollow(userId);
    }

    @Operation(summary = "是否已关注")
    @Parameter(name = "userId", description = "目标用户 ID", required = true)
    @GetMapping("/is-following/{userId}")
    public Boolean isFollowing(@PathVariable Long userId) {
        return followService.isFollowing(userId);
    }

    @Operation(summary = "我的关注列表", description = "含昵称头像与互关标记")
    @GetMapping("/following")
    public PageResp<SocialUserBriefResp> following(PageQuery pageQuery) {
        return followService.followingPage(pageQuery);
    }

    @Operation(summary = "我的粉丝列表")
    @GetMapping("/followers")
    public PageResp<SocialUserBriefResp> followers(PageQuery pageQuery) {
        return followService.followerPage(pageQuery);
    }

    @Operation(summary = "用户社交计数", description = "关注数/粉丝数/动态数/我是否已关注(用户主页头部)")
    @Parameter(name = "userId", description = "目标用户 ID", required = true)
    @GetMapping("/counts/{userId}")
    public SocialCountsResp counts(@PathVariable Long userId) {
        return followService.counts(userId);
    }
}
