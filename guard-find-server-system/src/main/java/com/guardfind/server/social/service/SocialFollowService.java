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

package com.guardfind.server.social.service;

import com.guardfind.server.social.model.resp.SocialCountsResp;
import com.guardfind.server.social.model.resp.SocialUserBriefResp;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;

/**
 * 社交关注业务接口(C 端)
 *
 * @author guardfind
 * @since 2026/10/29
 */
public interface SocialFollowService {

    /**
     * 关注(幂等;不能关注自己;会向对方发"新粉丝"站内信)
     *
     * @return true=本次新关注,false=原本已关注
     */
    boolean follow(Long targetUserId);

    /**
     * 取消关注(幂等)
     */
    void unfollow(Long targetUserId);

    /**
     * 我是否关注了某用户
     */
    boolean isFollowing(Long targetUserId);

    /**
     * 我的关注列表(分页,含互关标记)
     */
    PageResp<SocialUserBriefResp> followingPage(PageQuery pageQuery);

    /**
     * 我的粉丝列表(分页,含互关标记)
     */
    PageResp<SocialUserBriefResp> followerPage(PageQuery pageQuery);

    /**
     * 某用户的关注/粉丝/动态计数(用户主页头部)
     */
    SocialCountsResp counts(Long targetUserId);
}
