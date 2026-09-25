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

package com.guardfind.server.app.service;

import com.guardfind.server.app.model.req.AppLoginReq;
import com.guardfind.server.app.model.req.AppPasswordReq;
import com.guardfind.server.app.model.req.AppResetPasswordReq;
import com.guardfind.server.app.model.req.AppSmsCodeReq;
import com.guardfind.server.app.model.resp.AppLoginResp;
import com.guardfind.server.app.model.resp.AppSmsCodeResp;
import jakarta.servlet.http.HttpServletRequest;

/**
 * C 端认证业务接口
 *
 * @author guardfind
 * @since 2026/9/24
 */
public interface AppAuthService {

    /**
     * 发送短信验证码
     *
     * @param req     请求参数
     * @param request 请求对象
     * @return 验证码有效期
     */
    AppSmsCodeResp sendSmsCode(AppSmsCodeReq req, HttpServletRequest request);

    /**
     * 登录（验证码登录未注册时自动建档）
     *
     * @param req     登录请求参数
     * @param request 请求对象
     * @return 登录响应参数
     */
    AppLoginResp login(AppLoginReq req, HttpServletRequest request);

    /**
     * 退出登录（幂等，未携带令牌时直接返回）
     *
     * @param request 请求对象
     */
    void logout(HttpServletRequest request);

    /**
     * 设置/修改密码
     *
     * @param userId  当前登录的 C 端用户 ID
     * @param req     请求参数
     * @param request 请求对象（用于识别并保留当前会话，可为 null）
     */
    void updatePassword(Long userId, AppPasswordReq req, HttpServletRequest request);

    /**
     * 重置密码（公开接口，凭短信验证码）
     *
     * @param req 请求参数
     */
    void resetPassword(AppResetPasswordReq req);
}
