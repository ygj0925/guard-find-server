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

package com.guardfind.server.plaza.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.guardfind.server.findcase.model.query.CaseFeedQuery;
import com.guardfind.server.findcase.model.resp.CasePublicResp;
import com.guardfind.server.plaza.model.resp.FeedCursorResp;
import com.guardfind.server.plaza.service.PlazaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.starter.ratelimiter.annotation.RateLimiter;
import top.continew.starter.ratelimiter.enums.LimitType;

import java.util.concurrent.TimeUnit;

/**
 * 搜索 API
 *
 * <p>
 * 复用广场的状态过滤与游标分页：搜索结果 MUST NOT 出现草稿、下架或已删除档案。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Tag(name = "搜索 API")
@SaIgnore
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/search")
public class SearchController {

    private final PlazaService plazaService;

    @Operation(summary = "搜索寻人档案", description = "姓名、别名、失踪地点、体貌特征匹配，关键词经长度与敏感词校验")
    @Parameter(name = "keyword", description = "关键词", example = "张三")
    @RateLimiter(name = "APP:SEARCH", rate = 30, interval = 1, unit = TimeUnit.MINUTES, type = LimitType.IP, message = "搜索过于频繁，请稍后再试")
    @GetMapping
    public FeedCursorResp<CasePublicResp> search(CaseFeedQuery query) {
        return plazaService.search(query);
    }

    @Operation(summary = "清空搜索历史", description = "仅登录用户，需 Sa-Token 会话")
    @PostMapping("/history/clear")
    public void clearHistory() {
        plazaService.clearSearchHistory();
    }
}
