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

package com.guardfind.server.app.interceptor;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.HandlerInterceptor;
import com.guardfind.server.app.context.AppUserContext;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.app.service.AppUserService;
import com.guardfind.server.app.util.AppTokenUtils;
import com.guardfind.server.app.util.StpAppUtil;
import top.continew.starter.core.util.ExceptionUtils;

import java.util.Objects;

/**
 * C 端登录态填充拦截器
 *
 * <p>
 * 职责单一：仅在当前请求携带有效 app 令牌时把 {@link AppUserContext} 填充进 {@link AppUserContextHolder}，
 * <b>不做任何拦截、不维护路由白名单</b>（/app/** 已在 sa-token.extension.security.excludes 中放行后台鉴权）。
 * 需要登录态的接口通过 {@link AppUserContextHolder#getUserIdRequired()} 自行拒绝未登录访问，
 * 允许匿名的接口（广场、搜索、详情）则可直接使用 {@link AppUserContextHolder#getUserId()}。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@RequiredArgsConstructor
public class AppAuthInterceptor implements HandlerInterceptor {

    private final ObjectProvider<AppUserService> appUserServiceProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            String token = AppTokenUtils.resolveToken(request);
            if (StrUtil.isBlank(token)) {
                return true;
            }
            // 令牌无效（后台 token、已注销或过期 token）时按未登录处理，交由业务层判定
            Long userId = StpAppUtil.getLoginId(token);
            if (userId == null) {
                return true;
            }
            AppUserContextHolder.setContext(this.resolveContext(token, userId));
        } catch (Exception e) {
            log.warn("解析 C 端登录态失败，按未登录处理：{}", e.getMessage());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        // 线程池复用，必须清理上下文，否则身份串号
        AppUserContextHolder.clear();
    }

    /**
     * 解析上下文：优先取 token session 缓存，缺失或与令牌归属不一致时回源补齐
     *
     * @param token  令牌
     * @param userId 令牌归属的 C 端用户 ID
     * @return 上下文
     */
    AppUserContext resolveContext(String token, Long userId) {
        AppUserContext cachedContext = ExceptionUtils.exToNull(() -> StpAppUtil.stpLogic()
            .getTokenSessionByToken(token)
            .getModel(AppUserContextHolder.CONTEXT_KEY, AppUserContext.class));
        if (cachedContext != null && Objects.equals(userId, cachedContext.getId())) {
            return cachedContext;
        }
        AppUserService appUserService = appUserServiceProvider.getIfAvailable();
        if (appUserService != null) {
            AppUserDO user = ExceptionUtils.exToNull(() -> appUserService.getById(userId));
            if (user != null) {
                return appUserService.buildContext(user);
            }
        }
        AppUserContext context = new AppUserContext();
        context.setId(userId);
        return context;
    }
}
