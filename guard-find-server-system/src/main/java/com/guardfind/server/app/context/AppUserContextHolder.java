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

package com.guardfind.server.app.context;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.ttl.TransmittableThreadLocal;
import com.guardfind.server.app.util.StpAppUtil;

/**
 * App 用户上下文持有器
 *
 * <p>
 * 注意：必须在请求结束时调用 {@link #clear()}，否则线程池复用会导致身份串号。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public final class AppUserContextHolder {

    private static final TransmittableThreadLocal<AppUserContext> CONTEXT_HOLDER = new TransmittableThreadLocal<>();

    /**
     * token session 中缓存上下文的键
     */
    public static final String CONTEXT_KEY = "APP_USER_CONTEXT";

    private AppUserContextHolder() {
    }

    /**
     * 设置上下文（同时写入 token session，便于跨请求复用）
     *
     * @param context 上下文
     */
    public static void setContext(AppUserContext context) {
        CONTEXT_HOLDER.set(context);
        SaTokenInfo tokenInfo = StpAppUtil.stpLogic().getTokenInfo();
        if (tokenInfo != null && ObjectUtil.isNotEmpty(tokenInfo.getTokenValue())) {
            StpAppUtil.stpLogic().getTokenSessionByToken(tokenInfo.getTokenValue()).set(CONTEXT_KEY, context);
        }
    }

    /**
     * 获取上下文
     *
     * @return 上下文，未登录时为 null
     */
    public static AppUserContext getContext() {
        AppUserContext context = CONTEXT_HOLDER.get();
        if (context != null) {
            return context;
        }
        String token = StpAppUtil.getTokenValue();
        if (ObjectUtil.isEmpty(token)) {
            return null;
        }
        try {
            context = StpAppUtil.stpLogic().getTokenSessionByToken(token).getModel(CONTEXT_KEY, AppUserContext.class);
        } catch (Exception e) {
            return null;
        }
        if (context != null) {
            CONTEXT_HOLDER.set(context);
        }
        return context;
    }

    /**
     * 获取当前登录的 App 用户 ID
     *
     * @return App 用户 ID，未登录时为 null
     */
    public static Long getUserId() {
        AppUserContext context = getContext();
        return context == null ? null : context.getId();
    }

    /**
     * 获取当前登录的 App 用户 ID，未登录时抛出未授权异常
     *
     * @return App 用户 ID
     */
    public static Long getUserIdRequired() {
        Long userId = getUserId();
        if (userId == null) {
            // 用业务异常显式携带 "401"，由 AppExceptionHandler 写入响应信封；
            // 若改用 BusinessException 会被框架统一映射成 "500"，客户端的"清会话+跳登录"就永远不会触发。
            throw new com.guardfind.server.app.exception.AppBizException(com.guardfind.server.app.exception.AppBizException.UNAUTHORIZED, "登录已过期，请重新登录");
        }
        return userId;
    }

    /**
     * 当前用户是否为志愿者
     *
     * @return true：志愿者
     */
    public static boolean isVolunteer() {
        AppUserContext context = getContext();
        return context != null && "VOLUNTEER".equals(context.getIdentityType());
    }

    /**
     * 清除上下文
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
    }
}
