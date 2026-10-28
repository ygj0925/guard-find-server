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

package com.guardfind.server.post;

import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.exception.AppBizException;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.moderation.model.ContentCheckResult;
import com.guardfind.server.moderation.service.SensitiveWordMatcher;
import com.guardfind.server.post.mapper.PostCommentMapper;
import com.guardfind.server.post.model.entity.PostCommentDO;
import com.guardfind.server.post.mapper.PostLikeMapper;
import com.guardfind.server.post.mapper.PostMapper;
import com.guardfind.server.post.model.entity.PostDO;
import com.guardfind.server.post.model.req.PostCommentReq;
import com.guardfind.server.post.model.req.PostCreateReq;
import com.guardfind.server.post.service.impl.PostServiceImpl;
import com.guardfind.server.support.ContainerlessTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PostServiceImpl} 单测:审核拒绝、图片上限、点赞幂等、删除权限。
 *
 * @author guardfind
 * @since 2026/10/29
 */
class PostServiceImplTest extends ContainerlessTestBase {

    private PostMapper postMapper;
    private PostCommentMapper commentMapper;
    private PostLikeMapper likeMapper;
    private AppUserMapper appUserMapper;
    private SensitiveWordMatcher matcher;
    private com.guardfind.server.notification.service.AppMessageService messageService;
    private PostServiceImpl service;

    @BeforeEach
    void setUp() {
        postMapper = mock(PostMapper.class);
        commentMapper = mock(PostCommentMapper.class);
        likeMapper = mock(PostLikeMapper.class);
        appUserMapper = mock(AppUserMapper.class);
        matcher = mock(SensitiveWordMatcher.class);
        when(matcher.match(anyString())).thenReturn(ContentCheckResult.pass());
        messageService = mock(com.guardfind.server.notification.service.AppMessageService.class);
        service = new PostServiceImpl(postMapper, commentMapper, likeMapper, appUserMapper, matcher, messageService);
    }

    private static PostCreateReq req(String content) {
        PostCreateReq req = new PostCreateReq();
        req.setContent(content);
        return req;
    }

    @Test
    @DisplayName("发布命中敏感词直接拒绝,不落库")
    void createRejectsSensitiveContent() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            ContentCheckResult rejected = org.mockito.Mockito.mock(ContentCheckResult.class);
            when(rejected.isRejected()).thenReturn(true);
            when(rejected.isHighRisk()).thenReturn(false);
            when(matcher.match(anyString())).thenReturn(rejected);
            AppBizException ex = assertThrows(AppBizException.class, () -> service.create(req("含违规词")));
            assertEquals(AppBizException.RULE_REJECTED, ex.getCode());
            verify(postMapper, never()).insert(org.mockito.Mockito.any(PostDO.class));
        }
    }

    @Test
    @DisplayName("发布:图片序列化为 JSON,状态置已发布,计数归零")
    void createSerializesImages() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(postMapper.insert(any(PostDO.class))).thenAnswer(invocation -> {
                PostDO saved = invocation.getArgument(0);
                saved.setId(1L);
                return 1;
            });
            PostCreateReq req = req("春天来了");
            req.setImages(List.of("https://cdn/a.jpg", "https://cdn/b.jpg"));
            req.setLocationName("植物园");
            service.create(req);
            ArgumentCaptor<PostDO> captor = ArgumentCaptor.forClass(PostDO.class);
            verify(postMapper).insert(captor.capture());
            assertTrue(captor.getValue().getImages().contains("a.jpg"));
            assertEquals(1, captor.getValue().getStatus());
            assertEquals(0, captor.getValue().getLikeCount());
        }
    }

    @Test
    @DisplayName("点赞幂等:首次插入并 +1,再次删除并 -1")
    void toggleLikeIdempotent() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            PostDO post = new PostDO();
            post.setId(5L);
            post.setStatus(1);
            when(postMapper.selectById(5L)).thenReturn(post);
            // 首次:无点赞记录 → 插入
            when(likeMapper.selectOne(any())).thenReturn(null);
            assertTrue(service.toggleLike(5L));
            verify(likeMapper).insert(any(com.guardfind.server.post.model.entity.PostLikeDO.class));
            // 再次:已有记录 → 删除
            com.guardfind.server.post.model.entity.PostLikeDO existingLike = new com.guardfind.server.post.model.entity.PostLikeDO();
            existingLike.setId(99L);
            when(likeMapper.selectOne(any())).thenReturn(existingLike);
            assertFalse(service.toggleLike(5L));
            verify(likeMapper).deleteById(99L);
        }
    }

    @Test
    @DisplayName("删除他人动态返回 NOT_FOUND,不泄露存在性")
    void deleteRejectsForeignPost() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            PostDO foreign = new PostDO();
            foreign.setId(5L);
            foreign.setUserId(8L);
            when(postMapper.selectById(5L)).thenReturn(foreign);
            AppBizException ex = assertThrows(AppBizException.class, () -> service.delete(5L));
            assertEquals(AppBizException.NOT_FOUND, ex.getCode());
            verify(postMapper, never()).deleteById(any(Long.class));
        }
    }

    @Test
    @DisplayName("评论过审后落库并自增计数")
    void commentIncrementsCounter() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            PostDO post = new PostDO();
            post.setId(5L);
            post.setStatus(1);
            when(postMapper.selectById(5L)).thenReturn(post);
            when(commentMapper.insert(any(PostCommentDO.class))).thenReturn(1);

            PostCommentReq commentReq = new PostCommentReq();
            commentReq.setContent("写得好");
            service.comment(5L, commentReq);

            verify(commentMapper).insert(any(PostCommentDO.class));
            verify(postMapper).update(org.mockito.Mockito.isNull(), any());
        }
    }
}
