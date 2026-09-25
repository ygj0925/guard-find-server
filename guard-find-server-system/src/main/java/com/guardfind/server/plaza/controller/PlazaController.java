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
import com.guardfind.server.plaza.model.req.TrackReq;
import com.guardfind.server.plaza.model.resp.FeedCursorResp;
import com.guardfind.server.plaza.model.resp.PlazaFilterResp;
import com.guardfind.server.plaza.model.resp.PlazaStatisticsResp;
import com.guardfind.server.findcase.model.resp.CasePublicResp;
import com.guardfind.server.plaza.service.PlazaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.continew.starter.ratelimiter.annotation.RateLimiter;
import top.continew.starter.ratelimiter.enums.LimitType;

import java.util.concurrent.TimeUnit;

/**
 * 广场 API（公开可读，服务端强制状态过滤）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Tag(name = "广场 API")
@SaIgnore
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/plaza")
public class PlazaController {

    private final PlazaService plazaService;

    @Operation(summary = "广场信息流", description = "sort 取值 latest/urgent/nearby/found，四流互斥且仅返回对外可见档案")
    @Parameter(name = "sort", description = "排序方式", example = "latest")
    @Parameter(name = "cursor", description = "游标，首屏不传")
    @RateLimiter(name = "APP:PLAZA:FEED", rate = 120, interval = 1, unit = TimeUnit.MINUTES, type = LimitType.IP, message = "浏览过于频繁，请稍后再试")
    @GetMapping("/feed")
    public FeedCursorResp<CasePublicResp> feed(@RequestParam(required = false) String sort,
                                               @RequestParam(required = false) String provinceCode,
                                               @RequestParam(required = false) String cityCode,
                                               @RequestParam(required = false) @Size(max = 50, message = "关键词长度不能超过 {max} 个字符") String keyword,
                                               @RequestParam(required = false) String cursor,
                                               @RequestParam(required = false) Integer size) {
        return plazaService.feed(sort, provinceCode, cityCode, keyword, cursor, size);
    }

    @Operation(summary = "广场筛选器", description = "区划树与字典项，前端不硬编码枚举")
    @GetMapping("/filters")
    public PlazaFilterResp filters() {
        return plazaService.filters();
    }

    @Operation(summary = "广场统计", description = "累计建档数、已找回数、近 30 日新增数、今日线索数")
    @GetMapping("/statistics")
    public PlazaStatisticsResp statistics() {
        return plazaService.statistics();
    }

    @Operation(summary = "广场埋点上报", description = "曝光/点击/分享，允许匿名上报，按 IP 限流")
    @RateLimiter(name = "APP:PLAZA:TRACK", rate = 120, interval = 1, unit = TimeUnit.MINUTES, type = LimitType.IP, message = "操作过于频繁，请稍后再试")
    @PostMapping("/track")
    public void track(@RequestBody @Valid TrackReq req) {
        plazaService.track(req);
    }
}
