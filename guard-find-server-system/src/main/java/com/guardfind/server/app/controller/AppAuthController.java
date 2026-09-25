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

import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.core.util.StrUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.guardfind.server.app.constant.AppAuthConstants;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.model.req.AppDeactivateReq;
import com.guardfind.server.app.model.req.AppLoginReq;
import com.guardfind.server.app.model.req.AppPasswordReq;
import com.guardfind.server.app.model.req.AppProfileReq;
import com.guardfind.server.app.model.req.AppResetPasswordReq;
import com.guardfind.server.app.model.req.AppSmsCodeReq;
import com.guardfind.server.app.model.resp.AppDeactivateResp;
import com.guardfind.server.app.model.resp.AppLoginResp;
import com.guardfind.server.app.model.resp.AppSmsCodeResp;
import com.guardfind.server.app.model.resp.AppUserResp;
import com.guardfind.server.app.service.AppAuthService;
import com.guardfind.server.app.service.AppUserService;
import com.guardfind.server.common.constant.CacheConstants;
import top.continew.starter.log.annotation.Log;
import top.continew.starter.ratelimiter.annotation.RateLimiter;
import top.continew.starter.ratelimiter.annotation.RateLimiters;
import top.continew.starter.ratelimiter.enums.LimitType;

import java.util.concurrent.TimeUnit;

/**
 * C 端账号与会话 API
 *
 * <p>
 * 路径与字段严格对齐《天下无拐接口契约 v1》§1；/app/** 已放行后台 Sa-Token 鉴权，C 端登录态由
 * {@code AppAuthInterceptor} 填充、由各接口通过 {@link AppUserContextHolder} 自行判定。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Tag(name = "C 端账号 API")
@Log(module = "App 账号")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/auth")
public class AppAuthController {

    private final AppAuthService appAuthService;
    private final AppUserService appUserService;

    @SaIgnore
    @Operation(summary = "发送短信验证码", description = "限流：同机 1/分、同号 1/分、同号 10/日")
    @RateLimiters({
        @RateLimiter(name = CacheConstants.CAPTCHA_KEY_PREFIX + "APP:IP", key = "#req.phone", rate = 1, interval = 1, unit = TimeUnit.MINUTES, type = LimitType.IP, message = "获取验证码操作太频繁，请稍后再试"),
        @RateLimiter(name = CacheConstants.CAPTCHA_KEY_PREFIX + "APP:PHONE", key = "#req.phone", rate = 1, interval = 1, unit = TimeUnit.MINUTES, message = "获取验证码操作太频繁，请稍后再试"),
        @RateLimiter(name = CacheConstants.CAPTCHA_KEY_PREFIX + "APP:DAY", key = "#req.phone", rate = 10, interval = 24, unit = TimeUnit.HOURS, message = "今日获取验证码次数已达上限，请稍后再试")})
    @PostMapping("/sms-code")
    public AppSmsCodeResp sendSmsCode(@RequestBody @Valid AppSmsCodeReq req, HttpServletRequest request) {
        return appAuthService.sendSmsCode(req, request);
    }

    @SaIgnore
    @Operation(summary = "登录", description = "手机号验证码登录（未注册自动建档）与账号密码登录")
    @PostMapping("/login")
    public AppLoginResp login(@RequestBody @Valid AppLoginReq req, HttpServletRequest request) {
        this.fillDeviceInfo(req, request);
        return appAuthService.login(req, request);
    }

    @Operation(summary = "退出登录", description = "当前令牌立即失效")
    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        // 未登录时直接拒绝（契约 §1.3：该接口需登录）
        AppUserContextHolder.getUserIdRequired();
        appAuthService.logout(request);
    }

    @Log(ignore = true)
    @Operation(summary = "获取当前用户信息", description = "手机号脱敏返回")
    @GetMapping("/me")
    public AppUserResp me() {
        return appUserService.getResp(AppUserContextHolder.getUserIdRequired());
    }

    @Operation(summary = "完善资料", description = "昵称、头像、性别、身份类型、常驻省市区")
    @PutMapping("/profile")
    public AppUserResp updateProfile(@RequestBody @Valid AppProfileReq req) {
        return appUserService.updateProfile(AppUserContextHolder.getUserIdRequired(), req);
    }

    @Log(ignore = true)
    @Operation(summary = "设置/修改密码", description = "已设置密码需校验当前密码；未设置密码通过短信核验后设置")
    @PostMapping("/password")
    public void updatePassword(@RequestBody @Valid AppPasswordReq req, HttpServletRequest request) {
        appAuthService.updatePassword(AppUserContextHolder.getUserIdRequired(), req, request);
    }

    @SaIgnore
    @Log(ignore = true)
    @Operation(summary = "重置密码", description = "公开接口，凭短信验证码重置")
    @PostMapping("/reset-password")
    public void resetPassword(@RequestBody @Valid AppResetPasswordReq req) {
        appAuthService.resetPassword(req);
    }

    @Log(ignore = true)
    @Operation(summary = "申请注销账号", description = "进入 15 日冷静期，期内重新登录即可撤销")
    @DeleteMapping("/account")
    public AppDeactivateResp deactivate(@RequestBody @Valid AppDeactivateReq req, HttpServletRequest request) {
        AppDeactivateResp resp = appUserService.deactivate(AppUserContextHolder.getUserIdRequired(), req);
        // 提交注销申请后当前会话立即失效
        appAuthService.logout(request);
        return resp;
    }

    /**
     * 设备信息兜底：请求体未携带时从请求头读取（契约 §1.2 未定义设备字段，设备登记为可选能力）
     *
     * @param req     登录请求参数
     * @param request 请求对象
     */
    private void fillDeviceInfo(AppLoginReq req, HttpServletRequest request) {
        if (req == null || request == null) {
            return;
        }
        if (StrUtil.isBlank(req.getDeviceId())) {
            req.setDeviceId(request.getHeader(AppAuthConstants.HEADER_DEVICE_ID));
        }
        if (StrUtil.isBlank(req.getDeviceName())) {
            req.setDeviceName(request.getHeader(AppAuthConstants.HEADER_DEVICE_NAME));
        }
        if (StrUtil.isBlank(req.getPlatform())) {
            req.setPlatform(request.getHeader(AppAuthConstants.HEADER_PLATFORM));
        }
        if (StrUtil.isBlank(req.getAppVersion())) {
            req.setAppVersion(request.getHeader(AppAuthConstants.HEADER_APP_VERSION));
        }
        if (StrUtil.isBlank(req.getOsVersion())) {
            req.setOsVersion(request.getHeader(AppAuthConstants.HEADER_OS_VERSION));
        }
    }
}
