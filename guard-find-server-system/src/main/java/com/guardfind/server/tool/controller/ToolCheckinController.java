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

package com.guardfind.server.tool.controller;

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
import org.springframework.web.bind.annotation.RestController;
import top.continew.starter.ratelimiter.annotation.RateLimiter;
import top.continew.starter.ratelimiter.enums.LimitType;

import com.guardfind.server.tool.model.req.ToolCheckinCreateReq;
import com.guardfind.server.tool.model.req.ToolCheckinReq;
import com.guardfind.server.tool.model.resp.ToolCheckinResp;
import com.guardfind.server.tool.service.ToolCheckinService;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 在线签到 API(发起/动态码签到/名单)。
 * 防代签:动态码 30 秒轮换 + 时间窗 + 一人一次 + 地点名校验。
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Tag(name = "在线签到 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/tool/checkin")
public class ToolCheckinController {

    private final ToolCheckinService checkinService;

    @Operation(summary = "发起签到", description = "自动生成动态码种子;时间窗缺省=现在起 2 小时")
    @RateLimiter(name = "APP:TOOL:CHECKIN", rate = 30, interval = 1, unit = TimeUnit.HOURS, type = LimitType.IP)
    @PostMapping
    public Long create(@RequestBody @Valid ToolCheckinCreateReq req) {
        return checkinService.create(req);
    }

    @Operation(summary = "我发起的签到列表")
    @GetMapping("/mine")
    public List<ToolCheckinResp> mine() {
        return checkinService.myActivities();
    }

    @Operation(summary = "签到活动详情", description = "发起人=动态码种子+签到名单;参与者=是否已签")
    @Parameter(name = "id", description = "活动 ID", required = true)
    @GetMapping("/{id}")
    public ToolCheckinResp detail(@PathVariable Long id) {
        return checkinService.detail(id);
    }

    @Operation(summary = "签到", description = "校验时间窗/动态码(30 秒轮换,容忍上一窗)/地点名一致/未重复")
    @Parameter(name = "id", description = "活动 ID", required = true)
    @RateLimiter(name = "APP:TOOL:CHECKIN:DO", rate = 20, interval = 1, unit = TimeUnit.MINUTES, type = LimitType.IP, message = "签到太频繁,请稍后再试")
    @PostMapping("/{id}/checkin")
    public Long checkin(@PathVariable Long id, @RequestBody @Valid ToolCheckinReq req) {
        return checkinService.checkin(id, req);
    }

    @Operation(summary = "我签到的活动列表")
    @GetMapping("/signed")
    public List<ToolCheckinResp> signedByMe() {
        return checkinService.signedByMe();
    }

    @Operation(summary = "删除签到活动", description = "仅发起人,级联删签到记录")
    @Parameter(name = "id", description = "活动 ID", required = true)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        checkinService.delete(id);
    }
}
