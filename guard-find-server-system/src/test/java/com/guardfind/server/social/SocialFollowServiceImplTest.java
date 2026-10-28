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

package com.guardfind.server.social;

import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.notification.service.AppMessageService;
import com.guardfind.server.post.mapper.PostMapper;
import com.guardfind.server.social.mapper.SocialFollowMapper;
import com.guardfind.server.social.model.entity.SocialFollowDO;
import com.guardfind.server.social.service.impl.SocialFollowServiceImpl;
import com.guardfind.server.support.ContainerlessTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.continew.starter.core.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 关注关系单测:不能关注自己、幂等、取消、通知触达。
 */
class SocialFollowServiceImplTest extends ContainerlessTestBase {

    private SocialFollowMapper followMapper;
    private AppUserMapper appUserMapper;
    private PostMapper postMapper;
    private AppMessageService messageService;
    private SocialFollowServiceImpl service;

    @BeforeEach
    void setUp() {
        followMapper = mock(SocialFollowMapper.class);
        appUserMapper = mock(AppUserMapper.class);
        postMapper = mock(PostMapper.class);
        messageService = mock(AppMessageService.class);
        service = new SocialFollowServiceImpl(followMapper, appUserMapper, postMapper, messageService);
    }

    @Test
    @DisplayName("不能关注自己")
    void followSelfRejected() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            assertThrows(BusinessException.class, () -> service.follow(7L));
            verify(followMapper, times(0)).insert(any(SocialFollowDO.class));
        }
    }

    @Test
    @DisplayName("首次关注落库并发通知;重复关注幂等不发通知")
    void followIdempotent() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            // 首次:无关系
            when(followMapper.selectCount(any())).thenReturn(0L);
            when(followMapper.insert(any(SocialFollowDO.class))).thenReturn(1);
            assertTrue(service.follow(8L));
            ArgumentCaptor<SocialFollowDO> captor = ArgumentCaptor.forClass(SocialFollowDO.class);
            verify(followMapper).insert(captor.capture());
            assertEquals(7L, captor.getValue().getFollowerId());
            assertEquals(8L, captor.getValue().getFollowingId());

            verify(messageService, times(1)).send(eq(8L), any(), any(), eq(7L), any(), any());

            // 重复:已有关系,不再插入不再通知
            when(followMapper.selectCount(any())).thenReturn(1L);
            assertFalse(service.follow(8L));
            verify(followMapper, times(1)).insert(any(SocialFollowDO.class));
            verify(messageService, times(1)).send(eq(8L), any(), any(), eq(7L), any(), any());
        }
    }

    @Test
    @DisplayName("取消关注:删除关系")
    void unfollowDeletes() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            service.unfollow(8L);
            verify(followMapper).delete(any());
        }
    }

    @Test
    @DisplayName("未登录 isFollowing 恒为 false(广场未登录可浏览)")
    void isFollowingWithoutLogin() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserId()).thenReturn(null);
            assertFalse(service.isFollowing(8L));
        }
    }
}
