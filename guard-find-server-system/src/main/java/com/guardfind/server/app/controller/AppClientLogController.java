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

package com.guardfind.server.app.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.model.req.AppClientLogReq;
import com.guardfind.server.app.util.ClientLogMasker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.starter.ratelimiter.annotation.RateLimiter;
import top.continew.starter.ratelimiter.enums.LimitType;

import java.util.concurrent.TimeUnit;

/**
 * App 客户端非致命错误上报(契约 8 节)。可选登录;只落应用日志,不入库。
 *
 * @author guardfind
 * @since 2026/11/10
 */
@Tag(name = "App 客户端日志 API")
@Validated
@RestController
@RequestMapping("/app")
public class AppClientLogController {

    private static final Logger CLIENT_LOG = LoggerFactory.getLogger("APP_CLIENT_LOG");
    private static final int EXTRA_MAX_LENGTH = 2000;

    @Operation(summary = "客户端日志上报", description = "可选登录;服务端强制掩码手机号/身份证/token,按 IP 限流")
    @RateLimiter(name = "APP:CLIENT_LOG", rate = 60, interval = 1, unit = TimeUnit.MINUTES, type = LimitType.IP, message = "上报过于频繁,请稍后再试")
    @PostMapping("/client-log")
    public void submit(@RequestBody @Valid AppClientLogReq req) {
        String line = format(req, AppUserContextHolder.getUserId());
        switch (req.getLevel()) {
            case "ERROR" -> CLIENT_LOG.error(line);
            case "WARN" -> CLIENT_LOG.warn(line);
            case "INFO" -> CLIENT_LOG.info(line);
            default -> CLIENT_LOG.debug(line);
        }
    }

    static String format(AppClientLogReq req, Long userId) {
        String extra = req.getExtra() == null || req.getExtra().isEmpty()
            ? ""
            : StrUtil.maxLength(JSONUtil.toJsonStr(req.getExtra()), EXTRA_MAX_LENGTH);
        String raw = StrUtil
            .format("user={} platform={} version={} route={} message={} extra={} stack={}", userId == null
                ? "-"
                : userId, StrUtil.nullToDefault(req.getPlatform(), "-"), StrUtil.nullToDefault(req
                    .getAppVersion(), "-"), StrUtil.nullToDefault(req.getRoute(), "-"), req.getMessage(), extra, StrUtil
                        .nullToEmpty(req.getStack()));
        // 换行转义成字面量,防客户端伪造日志行
        return ClientLogMasker.mask(raw).replace("\r", "\\r").replace("\n", "\\n");
    }
}
