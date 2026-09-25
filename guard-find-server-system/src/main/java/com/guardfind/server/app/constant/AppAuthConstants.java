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

package com.guardfind.server.app.constant;

import com.guardfind.server.common.constant.CacheConstants;

/**
 * App（C 端）账号与登录常量
 *
 * <p>
 * 缓存键约定：C 端短信验证码使用 {@code CAPTCHA:app:{phone}} 前缀，与后台 {@code CAPTCHA:{phone}} 物理隔离，
 * 避免同一手机号在 C 端与后台共用验证码造成越权风险；行为（图形）验证码复用后台 {@code CAPTCHA:{captchaId}}。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public class AppAuthConstants {

    /**
     * App 客户端 ID（对应 sys_client 中 client_type = APP 的行，id = 100）
     *
     * @see db/changelog/mysql/guardfind_business_data.sql
     */
    public static final String APP_CLIENT_ID = "a7d3f1c9b5e24086f1a9c4d7e0b23158";

    /**
     * 令牌名称（与 sa-token.token-name 配置一致，App 以该请求头携带 token）
     */
    public static final String TOKEN_NAME = "Authorization";

    /**
     * 令牌前缀（部分网关会带 Bearer 前缀，解析时兼容剔除）
     */
    public static final String TOKEN_PREFIX = "Bearer ";

    /**
     * 默认昵称前缀（"无脚星友" + 4 位随机数字）
     */
    public static final String DEFAULT_NICKNAME_PREFIX = "无脚星友";

    /**
     * 默认昵称随机位长度
     */
    public static final int NICKNAME_RANDOM_LENGTH = 4;

    /**
     * 默认实名状态
     */
    public static final String DEFAULT_REAL_NAME_STATUS = "UNCHECKED";

    /**
     * 默认注册来源
     */
    public static final String DEFAULT_REGISTRATION_SOURCE = "APP";

    /**
     * 注销冷静期（天）
     */
    public static final int DEACTIVATE_COOLDOWN_DAYS = 15;

    /**
     * 密码连续错误锁定阈值（次）
     */
    public static final int MAX_PASSWORD_ERROR_COUNT = 5;

    /**
     * 密码错误达到阈值后的锁定时长（分钟）
     */
    public static final int PASSWORD_ERROR_LOCK_MINUTES = 15;

    /**
     * 短信验证码连续错误达到该次数后，要求携带行为（图形）验证码
     */
    public static final int SMS_CODE_ERROR_CAPTCHA_THRESHOLD = 3;

    /**
     * 短信验证码错误计数有效期（分钟）
     */
    public static final int SMS_CODE_ERROR_MINUTES = 15;

    /**
     * 设备标识请求头
     */
    public static final String HEADER_DEVICE_ID = "X-Device-Id";

    /**
     * 设备名称请求头
     */
    public static final String HEADER_DEVICE_NAME = "X-Device-Name";

    /**
     * 平台请求头
     */
    public static final String HEADER_PLATFORM = "X-Platform";

    /**
     * App 版本请求头
     */
    public static final String HEADER_APP_VERSION = "X-App-Version";

    /**
     * 系统版本请求头
     */
    public static final String HEADER_OS_VERSION = "X-Os-Version";

    /**
     * 平台缺省值（客户端未上报设备信息时登记）
     */
    public static final String DEFAULT_PLATFORM = "UNKNOWN";

    /**
     * C 端短信验证码键：CAPTCHA:app:{phone}
     *
     * @param phone 手机号
     * @return 缓存键
     */
    public static String appSmsCodeKey(String phone) {
        return CacheConstants.CAPTCHA_KEY_PREFIX + "app:" + phone;
    }

    /**
     * C 端短信验证码错误计数键
     *
     * @param phone 手机号
     * @return 缓存键
     */
    public static String appSmsCodeErrorKey(String phone) {
        return CacheConstants.CAPTCHA_KEY_PREFIX + "app:error:" + phone;
    }

    /**
     * C 端密码错误计数键
     *
     * @param account 登录账号
     * @return 缓存键
     */
    public static String appPasswordErrorKey(String account) {
        return CacheConstants.USER_PASSWORD_ERROR_KEY_PREFIX + "app:" + account;
    }

    private AppAuthConstants() {
    }
}
