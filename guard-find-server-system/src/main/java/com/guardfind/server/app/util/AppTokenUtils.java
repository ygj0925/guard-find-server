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

package com.guardfind.server.app.util;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import com.guardfind.server.app.constant.AppAuthConstants;

/**
 * C 端令牌解析工具
 *
 * <p>
 * C 端登录域（loginType = app）独立于后台，Sa-Token 默认按 {@code sa-token.token-name} 读取令牌；
 * 为避免受登录域令牌名拼接规则影响，这里统一从 {@code Authorization} 请求头显式解析。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public final class AppTokenUtils {

    private AppTokenUtils() {
    }

    /**
     * 从请求中解析 C 端令牌
     *
     * @param request 请求对象
     * @return 令牌，未携带时为 null
     */
    public static String resolveToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String token = request.getHeader(AppAuthConstants.TOKEN_NAME);
        if (StrUtil.isBlank(token)) {
            return null;
        }
        token = StrUtil.trim(token);
        if (StrUtil.startWithIgnoreCase(token, AppAuthConstants.TOKEN_PREFIX)) {
            token = StrUtil.trim(StrUtil.removePrefixIgnoreCase(token, AppAuthConstants.TOKEN_PREFIX));
        }
        return StrUtil.trimToNull(token);
    }
}
