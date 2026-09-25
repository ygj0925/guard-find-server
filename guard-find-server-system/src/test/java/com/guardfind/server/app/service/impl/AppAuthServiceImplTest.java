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

package com.guardfind.server.app.service.impl;

import com.guardfind.server.app.constant.AppAuthConstants;
import com.guardfind.server.app.enums.AppSmsSceneEnum;
import com.guardfind.server.app.model.AppLoginUser;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.app.model.req.AppAccountLoginReq;
import com.guardfind.server.app.model.req.AppPhoneLoginReq;
import com.guardfind.server.app.model.req.AppSmsCodeReq;
import com.guardfind.server.app.model.resp.AppLoginResp;
import com.guardfind.server.app.service.AppUserService;
import com.guardfind.server.auth.enums.AuthTypeEnum;
import com.guardfind.server.common.config.CaptchaProperties;
import com.guardfind.server.common.enums.DisEnableStatusEnum;
import com.guardfind.server.system.model.resp.ClientResp;
import com.guardfind.server.system.service.ClientService;
import com.guardfind.server.system.service.SmsConfigService;
import com.guardfind.server.support.ContainerlessTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import top.continew.starter.cache.redisson.util.RedisUtils;
import top.continew.starter.core.exception.BadRequestException;
import top.continew.starter.core.exception.BusinessException;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C 端认证业务单元测试（不加载 Spring 上下文，不依赖 MySQL/Redis）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppAuthServiceImplTest extends ContainerlessTestBase {

    private static final String PHONE = "13800001234";
    private static final String SMS_CODE_KEY = "CAPTCHA:app:" + PHONE;
    private static final String SMS_CODE_ERROR_KEY = "CAPTCHA:app:error:" + PHONE;
    private static final String PASSWORD_ERROR_KEY = "USER:PASSWORD_ERROR:app:" + PHONE;
    private static final Long USER_ID = 1789000000000000001L;

    @Mock
    private AppUserService appUserService;
    @Mock
    private ClientService clientService;
    @Mock
    private CaptchaProperties captchaProperties;
    @Mock
    private SmsConfigService smsConfigService;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AppAuthServiceImpl appAuthService;

    @BeforeEach
    void setUp() {
        // authenticate / doSendSmsCode 分别依赖 Sa-Token 与短信通道，测试中以 spy 打桩隔离
        appAuthService = spy(new AppAuthServiceImpl(appUserService, clientService, captchaProperties, smsConfigService, passwordEncoder));
    }

    @Test
    @DisplayName("校验短信验证码：验证码正确时通过并一次性消费")
    void verifyAppSmsCodeShouldPassWhenCodeMatched() {
        try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            redis.when(() -> RedisUtils.get(SMS_CODE_KEY)).thenReturn("123456");
            assertThatNoException().isThrownBy(() -> appAuthService.verifyAppSmsCode(PHONE, "123456"));
            redis.verify(() -> RedisUtils.delete(SMS_CODE_KEY));
        }
    }

    @Test
    @DisplayName("校验短信验证码：验证码已失效时报错")
    void verifyAppSmsCodeShouldFailWhenExpired() {
        try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            redis.when(() -> RedisUtils.get(SMS_CODE_KEY)).thenReturn(null);
            assertThatExceptionOfType(BadRequestException.class).isThrownBy(() -> appAuthService
                .verifyAppSmsCode(PHONE, "123456")).withMessage("验证码已失效，请重新获取");
        }
    }

    @Test
    @DisplayName("校验短信验证码：验证码错误时报错并删除缓存")
    void verifyAppSmsCodeShouldFailWhenCodeWrong() {
        try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            redis.when(() -> RedisUtils.get(SMS_CODE_KEY)).thenReturn("654321");
            assertThatExceptionOfType(BadRequestException.class).isThrownBy(() -> appAuthService
                .verifyAppSmsCode(PHONE, "123456")).withMessage("验证码不正确");
            redis.verify(() -> RedisUtils.delete(SMS_CODE_KEY));
        }
    }

    @Test
    @DisplayName("登录：验证码错误时不建档、不签发令牌")
    void loginShouldNotRegisterWhenCodeWrong() {
        when(clientService.getByClientId(AppAuthConstants.APP_CLIENT_ID)).thenReturn(buildClient());
        Map<String, Object> cache = new HashMap<>();
        cache.put(SMS_CODE_KEY, "654321");
        try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            stubCache(redis, cache);
            assertThatExceptionOfType(BadRequestException.class).isThrownBy(() -> appAuthService
                .login(buildPhoneLoginReq("123456"), null)).withMessage("验证码不正确");
        }
        verify(appUserService, never()).obtainOrRegisterByPhone(anyString());
        verify(appAuthService, never()).authenticate(any(), any(), any());
    }

    @Test
    @DisplayName("登录：首次验证码登录自动建档，isNewUser 为 true 且返回可用令牌")
    void loginShouldAutoRegisterOnFirstPhoneLogin() {
        when(clientService.getByClientId(AppAuthConstants.APP_CLIENT_ID)).thenReturn(buildClient());
        when(appUserService.obtainOrRegisterByPhone(PHONE)).thenReturn(new AppLoginUser(buildUser(1), true));
        doReturn("app-token").when(appAuthService).authenticate(any(), any(), any());
        Map<String, Object> cache = new HashMap<>();
        cache.put(SMS_CODE_KEY, "123456");
        try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            stubCache(redis, cache);
            AppLoginResp resp = appAuthService.login(buildPhoneLoginReq("123456"), null);
            assertThat(resp.getToken()).isEqualTo("app-token");
            assertThat(resp.getTokenName()).isEqualTo("Authorization");
            assertThat(resp.getExpiresIn()).isEqualTo(2592000L);
            assertThat(resp.getIsNewUser()).isTrue();
        }
        verify(appUserService).afterLogin(any(), any());
    }

    @Test
    @DisplayName("登录：冷静期内重新登录撤销注销申请")
    void loginShouldCancelDeactivationInCooldown() {
        AppUserDO user = buildUser(3);
        when(clientService.getByClientId(AppAuthConstants.APP_CLIENT_ID)).thenReturn(buildClient());
        when(appUserService.obtainOrRegisterByPhone(PHONE)).thenReturn(new AppLoginUser(user, false));
        when(appUserService.cancelDeactivationIfCancelling(user)).thenReturn(true);
        doReturn("app-token").when(appAuthService).authenticate(any(), any(), any());
        Map<String, Object> cache = new HashMap<>();
        cache.put(SMS_CODE_KEY, "123456");
        try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            stubCache(redis, cache);
            AppLoginResp resp = appAuthService.login(buildPhoneLoginReq("123456"), null);
            assertThat(resp.getDeactivationCancelled()).isTrue();
            assertThat(resp.getToken()).isEqualTo("app-token");
        }
    }

    @Test
    @DisplayName("登录：账号已注销时拒绝登录")
    void loginShouldRejectCancelledAccount() {
        when(clientService.getByClientId(AppAuthConstants.APP_CLIENT_ID)).thenReturn(buildClient());
        when(appUserService.obtainOrRegisterByPhone(PHONE)).thenReturn(new AppLoginUser(buildUser(4), false));
        Map<String, Object> cache = new HashMap<>();
        cache.put(SMS_CODE_KEY, "123456");
        try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            stubCache(redis, cache);
            assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> appAuthService
                .login(buildPhoneLoginReq("123456"), null)).withMessage("账号已注销");
        }
        verify(appAuthService, never()).authenticate(any(), any(), any());
    }

    @Test
    @DisplayName("登录：连续 5 次密码错误触发锁定，第 6 次即使密码正确也不签发令牌")
    void loginShouldLockAccountAfterFivePasswordErrors() {
        AppUserDO user = buildUser(1);
        user.setPassword("$2a$10$encrypted");
        when(clientService.getByClientId(AppAuthConstants.APP_CLIENT_ID)).thenReturn(buildClient());
        when(appUserService.findByPhone(PHONE)).thenReturn(user);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        Map<String, Object> cache = new HashMap<>();
        try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            stubCache(redis, cache);
            for (int i = 1; i <= 4; i++) {
                assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> appAuthService
                    .login(buildAccountLoginReq("wrong"), null)).withMessage("账号或密码不正确");
            }
            assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> appAuthService
                .login(buildAccountLoginReq("wrong"), null)).withMessageContaining("已被锁定");
            assertThat(cache.get(PASSWORD_ERROR_KEY)).isEqualTo(5);
            // 第 6 次：密码正确也直接返回锁定错误
            when(passwordEncoder.matches("right", "$2a$10$encrypted")).thenReturn(true);
            assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> appAuthService
                .login(buildAccountLoginReq("right"), null)).withMessageContaining("已被锁定");
        }
        verify(appAuthService, never()).authenticate(any(), any(), any());
    }

    @Test
    @DisplayName("登录：密码正确时清除密码错误计数")
    void loginShouldClearErrorCountWhenPasswordMatched() {
        AppUserDO user = buildUser(1);
        user.setPassword("$2a$10$encrypted");
        when(clientService.getByClientId(AppAuthConstants.APP_CLIENT_ID)).thenReturn(buildClient());
        when(appUserService.findByPhone(PHONE)).thenReturn(user);
        when(passwordEncoder.matches("right", "$2a$10$encrypted")).thenReturn(true);
        doReturn("app-token").when(appAuthService).authenticate(any(), any(), any());
        Map<String, Object> cache = new HashMap<>();
        cache.put(PASSWORD_ERROR_KEY, 2);
        try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            stubCache(redis, cache);
            AppLoginResp resp = appAuthService.login(buildAccountLoginReq("right"), null);
            assertThat(resp.getToken()).isEqualTo("app-token");
            assertThat(resp.getIsNewUser()).isFalse();
            assertThat(cache).doesNotContainKey(PASSWORD_ERROR_KEY);
        }
    }

    @Test
    @DisplayName("登录：客户端未授权该认证类型时拒绝")
    void loginShouldRejectUnsupportedAuthType() {
        ClientResp client = buildClient();
        client.setAuthType(List.of("PHONE"));
        when(clientService.getByClientId(AppAuthConstants.APP_CLIENT_ID)).thenReturn(client);
        assertThatExceptionOfType(BadRequestException.class).isThrownBy(() -> appAuthService
            .login(buildAccountLoginReq("any"), null)).withMessage("该客户端暂未授权 [账号] 认证");
    }

    @Test
    @DisplayName("短信验证码：LOGIN 场景允许未注册手机号，RESET 场景要求已注册")
    void sendSmsCodeShouldCheckScene() {
        when(captchaProperties.getSms()).thenReturn(buildCaptchaSms());
        when(appUserService.findByPhone(PHONE)).thenReturn(null);
        doNothing().when(appAuthService).doSendSmsCode(anyString(), anyString(), anyLong());
        Map<String, Object> cache = new HashMap<>();
        try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            stubCache(redis, cache);
            assertThat(appAuthService.sendSmsCode(buildSmsCodeReq(AppSmsSceneEnum.LOGIN), null).getExpireSeconds())
                .isEqualTo(300L);
            assertThat(cache.get(SMS_CODE_KEY)).asString().hasSize(6);
            assertThatExceptionOfType(BadRequestException.class).isThrownBy(() -> appAuthService
                .sendSmsCode(buildSmsCodeReq(AppSmsSceneEnum.RESET), null)).withMessage("该手机号未注册");
        }
    }

    /**
     * 以 Map 模拟 Redis，避免依赖真实 Redis
     *
     * <p>
     * 说明：静态打桩必须逐条写成独立语句（Mockito 只允许参数匹配器出现在 when/verify 内部），
     * 且 {@code RedisUtils#delete} 返回 boolean，不可返回 Long。
     * </p>
     */
    private void stubCache(MockedStatic<RedisUtils> redis, Map<String, Object> cache) {
        redis.when(() -> RedisUtils.get(anyString()))
            .thenAnswer(invocation -> cache.get(invocation.<String>getArgument(0)));
        redis.when(() -> RedisUtils.set(anyString(), any(), any(Duration.class))).thenAnswer(invocation -> {
            cache.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        });
        redis.when(() -> RedisUtils.delete(anyString())).thenAnswer(invocation -> {
            cache.remove(invocation.<String>getArgument(0));
            return true;
        });
        redis.when(() -> RedisUtils.getTimeToLive(anyString())).thenReturn(600000L);
    }

    private ClientResp buildClient() {
        ClientResp client = new ClientResp();
        client.setClientId(AppAuthConstants.APP_CLIENT_ID);
        client.setClientType("APP");
        client.setAuthType(List.of("PHONE", "ACCOUNT"));
        client.setActiveTimeout(604800L);
        client.setTimeout(2592000L);
        client.setIsConcurrent(true);
        client.setMaxLoginCount(3);
        client.setStatus(DisEnableStatusEnum.ENABLE);
        return client;
    }

    private AppUserDO buildUser(Integer accountStatus) {
        AppUserDO user = new AppUserDO();
        user.setId(USER_ID);
        user.setNickname("无脚星友4821");
        user.setPhone(PHONE);
        user.setAccountStatus(accountStatus);
        user.setIdentityType("PUBLIC");
        return user;
    }

    private AppPhoneLoginReq buildPhoneLoginReq(String code) {
        AppPhoneLoginReq req = new AppPhoneLoginReq();
        req.setAuthType(AuthTypeEnum.PHONE);
        req.setPhone(PHONE);
        req.setCode(code);
        req.setDeviceId("device-1");
        return req;
    }

    private AppAccountLoginReq buildAccountLoginReq(String password) {
        AppAccountLoginReq req = new AppAccountLoginReq();
        req.setAuthType(AuthTypeEnum.ACCOUNT);
        req.setAccount(PHONE);
        req.setPassword(password);
        return req;
    }

    private AppSmsCodeReq buildSmsCodeReq(AppSmsSceneEnum scene) {
        AppSmsCodeReq req = new AppSmsCodeReq();
        req.setPhone(PHONE);
        req.setScene(scene);
        return req;
    }

    private CaptchaProperties.CaptchaSms buildCaptchaSms() {
        CaptchaProperties.CaptchaSms sms = new CaptchaProperties.CaptchaSms();
        sms.setLength(6);
        sms.setExpirationInMinutes(5);
        return sms;
    }
}
