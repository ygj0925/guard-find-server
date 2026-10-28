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

package com.guardfind.server.post.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.exception.AppBizException;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.moderation.model.ContentCheckResult;
import com.guardfind.server.moderation.service.SensitiveWordMatcher;
import com.guardfind.server.post.mapper.PostCommentMapper;
import com.guardfind.server.post.mapper.PostLikeMapper;
import com.guardfind.server.post.mapper.PostMapper;
import com.guardfind.server.post.model.entity.PostCommentDO;
import com.guardfind.server.post.model.entity.PostDO;
import com.guardfind.server.post.model.entity.PostLikeDO;
import com.guardfind.server.post.model.req.PostCommentReq;
import com.guardfind.server.post.model.req.PostCreateReq;
import com.guardfind.server.post.model.resp.PostCommentResp;
import com.guardfind.server.post.model.resp.PostResp;
import com.guardfind.server.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 社区动态实现。
 * 合规:发布与评论均过敏感词审核,命中直接拒绝(与线索口径一致);
 * 位置为用户主动提供的名称+可选坐标,不申请定位权限。
 * 点赞幂等(唯一约束),计数以 update wrapper 自增/自减维护。
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private static final int MAX_IMAGES = 9;

    private final PostMapper postMapper;
    private final PostCommentMapper commentMapper;
    private final PostLikeMapper likeMapper;
    private final AppUserMapper appUserMapper;
    private final SensitiveWordMatcher sensitiveWordMatcher;

    @Override
    public Long create(PostCreateReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ContentCheckResult check = sensitiveWordMatcher.match(req.getContent());
        if (check.isRejected() || check.isHighRisk()) {
            throw new AppBizException(AppBizException.RULE_REJECTED, "内容包含违规表述,请修改后发布");
        }
        PostDO post = new PostDO();
        post.setUserId(userId);
        post.setContent(StrUtil.trim(req.getContent()));
        List<String> images = req.getImages();
        if (images != null && !images.isEmpty()) {
            if (images.size() > MAX_IMAGES) {
                throw new BusinessException("图片最多 9 张");
            }
            post.setImages(JSONUtil.toJsonStr(images));
        }
        post.setLocationName(StrUtil.trimToNull(req.getLocationName()));
        post.setLongitude(req.getLongitude());
        post.setLatitude(req.getLatitude());
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setStatus(1);
        postMapper.insert(post);
        return post.getId();
    }

    @Override
    public PageResp<PostResp> page(PageQuery pageQuery) {
        return pageBy(null, pageQuery);
    }

    @Override
    public PageResp<PostResp> mine(PageQuery pageQuery) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        return pageBy(userId, pageQuery);
    }

    private PageResp<PostResp> pageBy(Long onlyUserId, PageQuery pageQuery) {
        Long viewerId = AppUserContextHolder.getUserId();
        Page<PostDO> page = postMapper.selectPage(new Page<>(pageNum(pageQuery), pageSize(pageQuery)), Wrappers
            .<PostDO>lambdaQuery()
            .eq(PostDO::getStatus, 1)
            .eq(onlyUserId != null, PostDO::getUserId, onlyUserId)
            .orderByDesc(PostDO::getCreateTime)
            .orderByDesc(PostDO::getId));
        List<PostResp> list = assemble(page.getRecords(), viewerId);
        PageResp<PostResp> resp = new PageResp<>();
        resp.setList(list);
        resp.setTotal(page.getTotal());
        return resp;
    }

    @Override
    public PostResp detail(Long id) {
        Long viewerId = AppUserContextHolder.getUserId();
        PostDO post = getPublished(id);
        return assemble(List.of(post), viewerId).get(0);
    }

    @Override
    public List<PostCommentResp> comments(Long postId) {
        getPublished(postId);
        List<PostCommentDO> comments = commentMapper.selectList(Wrappers.<PostCommentDO>lambdaQuery()
            .eq(PostCommentDO::getPostId, postId)
            .orderByAsc(PostCommentDO::getId));
        List<Long> userIds = comments.stream().map(PostCommentDO::getUserId).distinct().toList();
        Map<Long, AppUserDO> users = userIds.isEmpty()
            ? Map.of()
            : appUserMapper.selectByIds(userIds)
                .stream()
                .collect(Collectors.toMap(AppUserDO::getId, Function.identity()));
        return comments.stream().map(comment -> {
            PostCommentResp resp = new PostCommentResp();
            resp.setId(comment.getId());
            resp.setUserId(comment.getUserId());
            resp.setContent(comment.getContent());
            resp.setCreateTime(comment.getCreateTime());
            AppUserDO user = users.get(comment.getUserId());
            if (user != null) {
                resp.setNickname(user.getNickname());
                resp.setAvatar(user.getAvatar());
            }
            return resp;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long comment(Long postId, PostCommentReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        getPublished(postId);
        ContentCheckResult check = sensitiveWordMatcher.match(req.getContent());
        if (check.isRejected() || check.isHighRisk()) {
            throw new AppBizException(AppBizException.RULE_REJECTED, "评论包含违规表述");
        }
        PostCommentDO comment = new PostCommentDO();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setContent(StrUtil.trim(req.getContent()));
        commentMapper.insert(comment);
        postMapper.update(null, Wrappers.<PostDO>update().eq("id", postId).setSql("comment_count = comment_count + 1"));
        return comment.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleLike(Long postId) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        getPublished(postId);
        PostLikeDO existing = likeMapper.selectOne(Wrappers.<PostLikeDO>lambdaQuery()
            .eq(PostLikeDO::getPostId, postId)
            .eq(PostLikeDO::getUserId, userId)
            .last("LIMIT 1"));
        if (existing != null) {
            likeMapper.deleteById(existing.getId());
            postMapper.update(null, Wrappers.<PostDO>update().eq("id", postId).setSql("like_count = like_count - 1"));
            return false;
        }
        PostLikeDO like = new PostLikeDO();
        like.setPostId(postId);
        like.setUserId(userId);
        likeMapper.insert(like);
        postMapper.update(null, Wrappers.<PostDO>update().eq("id", postId).setSql("like_count = like_count + 1"));
        return true;
    }

    @Override
    public void delete(Long postId) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        PostDO post = postMapper.selectById(postId);
        if (post == null || !Objects.equals(post.getUserId(), userId)) {
            throw new AppBizException(AppBizException.NOT_FOUND, "动态不存在");
        }
        postMapper.deleteById(post.getId());
    }

    private PostDO getPublished(Long postId) {
        if (postId == null) {
            throw new BusinessException("动态 ID 不能为空");
        }
        PostDO post = postMapper.selectById(postId);
        if (post == null || post.getStatus() == null || post.getStatus() != 1) {
            throw new AppBizException(AppBizException.NOT_FOUND, "动态不存在");
        }
        return post;
    }

    /** 批量补作者信息与 likedByMe,避免 N+1 */
    private List<PostResp> assemble(List<PostDO> posts, Long viewerId) {
        List<Long> userIds = posts.stream().map(PostDO::getUserId).distinct().toList();
        Map<Long, AppUserDO> users = userIds.isEmpty()
            ? Map.of()
            : appUserMapper.selectByIds(userIds)
                .stream()
                .collect(Collectors.toMap(AppUserDO::getId, Function.identity()));
        Set<Long> likedPostIds;
        if (viewerId == null || posts.isEmpty()) {
            likedPostIds = Set.of();
        } else {
            List<Long> postIds = posts.stream().map(PostDO::getId).toList();
            likedPostIds = likeMapper.selectList(Wrappers.<PostLikeDO>lambdaQuery()
                .in(PostLikeDO::getPostId, postIds)
                .eq(PostLikeDO::getUserId, viewerId)).stream().map(PostLikeDO::getPostId).collect(Collectors.toSet());
        }
        return posts.stream().map(post -> {
            PostResp resp = new PostResp();
            resp.setId(post.getId());
            resp.setUserId(post.getUserId());
            resp.setContent(post.getContent());
            resp.setImages(StrUtil.isBlank(post.getImages())
                ? List.of()
                : JSONUtil.toList(post.getImages(), String.class));
            resp.setLocationName(post.getLocationName());
            resp.setLongitude(post.getLongitude());
            resp.setLatitude(post.getLatitude());
            resp.setLikeCount(post.getLikeCount());
            resp.setCommentCount(post.getCommentCount());
            resp.setLikedByMe(likedPostIds.contains(post.getId()));
            resp.setCreateTime(post.getCreateTime());
            AppUserDO user = users.get(post.getUserId());
            if (user != null) {
                resp.setNickname(user.getNickname());
                resp.setAvatar(user.getAvatar());
            }
            return resp;
        }).toList();
    }

    private int pageNum(PageQuery pageQuery) {
        return pageQuery == null || pageQuery.getPage() == null ? 1 : pageQuery.getPage();
    }

    private int pageSize(PageQuery pageQuery) {
        return pageQuery == null || pageQuery.getSize() == null ? 10 : pageQuery.getSize();
    }
}
