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

import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.dev33.satoken.stp.parameter.enums.SaLogoutMode;
import cn.dev33.satoken.stp.parameter.enums.SaReplacedRange;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.entity.SmsResponse;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.guardfind.server.app.constant.AppAuthConstants;
import com.guardfind.server.app.context.AppUserContext;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.enums.AccountStatusEnum;
import com.guardfind.server.app.enums.AppSmsSceneEnum;
import com.guardfind.server.app.model.AppLoginUser;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.app.model.req.AppAccountLoginReq;
import com.guardfind.server.app.model.req.AppLoginReq;
import com.guardfind.server.app.model.req.AppPasswordReq;
import com.guardfind.server.app.model.req.AppPhoneLoginReq;
import com.guardfind.server.app.model.req.AppResetPasswordReq;
import com.guardfind.server.app.model.req.AppSmsCodeReq;
import com.guardfind.server.app.model.resp.AppLoginResp;
import com.guardfind.server.app.model.resp.AppSmsCodeResp;
import com.guardfind.server.app.service.AppAuthService;
import com.guardfind.server.app.service.AppUserService;
import com.guardfind.server.app.util.AppTokenUtils;
import com.guardfind.server.app.util.StpAppUtil;
import com.guardfind.server.auth.enums.AuthTypeEnum;
import com.guardfind.server.common.config.CaptchaProperties;
import com.guardfind.server.common.constant.CacheConstants;
import com.guardfind.server.common.context.UserExtraContext;
import com.guardfind.server.common.enums.DisEnableStatusEnum;
import com.guardfind.server.system.enums.PasswordPolicyEnum;
import com.guardfind.server.system.model.entity.SmsConfigDO;
import com.guardfind.server.system.model.resp.ClientResp;
import com.guardfind.server.system.service.ClientService;
import com.guardfind.server.system.service.SmsConfigService;
import top.continew.starter.cache.redisson.util.RedisUtils;
import top.continew.starter.core.exception.BaseException;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.core.util.ExceptionUtils;
import top.continew.starter.core.util.ServletUtils;
import top.continew.starter.core.util.validation.CheckUtils;
import top.continew.starter.core.util.validation.ValidationUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * C 端认证业务实现
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppAuthServiceImpl implements AppAuthService {

    private static final String CLIENT_ID = "clientId";
    private static final String CAPTCHA_EXPIRED = "验证码已失效，请重新获取";
    private static final String CAPTCHA_ERROR = "验证码不正确";
    private static final String LOGIN_ERROR = "账号或密码不正确";
    private static final int DEFAULT_SMS_CODE_LENGTH = 6;
    private static final long DEFAULT_SMS_CODE_MINUTES = 5L;

    private final AppUserService appUserService;
    private final ClientService clientService;
    private final CaptchaProperties captchaProperties;
    private final SmsConfigService smsConfigService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public AppSmsCodeResp sendSmsCode(AppSmsCodeReq req, HttpServletRequest request) {
        ValidationUtils.throwIfNull(req, "请填写手机号");
        String phone = StrUtil.trim(req.getPhone());
        this.checkSmsCodeScene(phone, req.getScene());
        long expirationInMinutes = this.getSmsCodeExpirationInMinutes();
        String code = RandomUtil.randomNumbers(this.getSmsCodeLength());
        this.doSendSmsCode(phone, code, expirationInMinutes);
        RedisUtils.set(AppAuthConstants.appSmsCodeKey(phone), code, Duration.ofMinutes(expirationInMinutes));
        AppSmsCodeResp resp = new AppSmsCodeResp();
        resp.setExpireSeconds(expirationInMinutes * 60);
        return resp;
    }

    @Override
    public AppLoginResp login(AppLoginReq req, HttpServletRequest request) {
        ValidationUtils.throwIfNull(req, "登录参数不能为空");
        AuthTypeEnum authType = req.getAuthType();
        ValidationUtils.throwIfNull(authType, "认证类型不能为空");
        // 校验客户端（会话策略取自 sys_client 的 APP 行，不在代码中硬编码）
        ClientResp client = this.getAppClient(authType);
        AppLoginUser loginUser = switch (authType) {
            case PHONE -> this.phoneLogin((AppPhoneLoginReq)req);
            case ACCOUNT -> this.accountLogin((AppAccountLoginReq)req, request);
            default -> throw new BusinessException("暂不支持该登录方式");
        };
        AppUserDO user = loginUser.getUser();
        ValidationUtils.throwIfNull(user, "账号不存在");
        this.checkAccountStatus(user);
        // 冷静期内登录：撤销注销申请
        boolean deactivationCancelled = appUserService.cancelDeactivationIfCancelling(user);
        String token = this.authenticate(user, client, req.getDeviceId());
        appUserService.afterLogin(user, req);
        AppLoginResp resp = new AppLoginResp();
        resp.setToken(token);
        resp.setTokenName(AppAuthConstants.TOKEN_NAME);
        resp.setExpiresIn(client.getTimeout());
        resp.setIsNewUser(loginUser.isNewUser());
        resp.setDeactivationCancelled(deactivationCancelled);
        resp.setUser(appUserService.getResp(user.getId()));
        return resp;
    }

    @Override
    public void logout(HttpServletRequest request) {
        String token = AppTokenUtils.resolveToken(request);
        if (StrUtil.isBlank(token)) {
            return;
        }
        try {
            StpAppUtil.logoutByToken(token);
        } catch (Exception e) {
            log.warn("C 端登出失败（令牌可能已失效）：{}", e.getMessage());
        }
    }

    @Override
    public void updatePassword(Long userId, AppPasswordReq req, HttpServletRequest request) {
        ValidationUtils.throwIfNull(req, "请填写密码信息");
        AppUserDO user = appUserService.getById(userId);
        boolean hasPassword = StrUtil.isNotBlank(user.getPassword());
        if (hasPassword) {
            ValidationUtils.throwIfBlank(req.getOldPassword(), "当前密码不能为空");
            CheckUtils.throwIf(!passwordEncoder.matches(req.getOldPassword(), user.getPassword()), "当前密码不正确");
            CheckUtils.throwIf(passwordEncoder.matches(req.getNewPassword(), user.getPassword()), "新密码不能与当前密码相同");
        } else {
            // 仅用验证码注册、未设置过密码：通过短信核验身份后设置密码
            ValidationUtils.throwIfBlank(req.getPhone(), "手机号不能为空");
            ValidationUtils.throwIfBlank(req.getCode(), "验证码不能为空");
            this.verifyAppSmsCode(StrUtil.trim(req.getPhone()), req.getCode());
            if (StrUtil.isBlank(user.getPhone())) {
                appUserService.bindPhone(userId, StrUtil.trim(req.getPhone()));
            } else {
                CheckUtils.throwIfNotEqual(StrUtil.trim(req.getPhone()), user.getPhone(), "手机号与当前账号绑定的号码不一致");
            }
        }
        appUserService.updatePassword(userId, req.getNewPassword());
        // 密码变更后清除错误计数；修改密码保留当前会话、下线其他设备
        RedisUtils.delete(AppAuthConstants.appPasswordErrorKey(StrUtil.blankToDefault(user.getPhone(), String
            .valueOf(userId))));
        this.kickOutOtherTokens(userId, AppTokenUtils.resolveToken(request));
    }

    @Override
    public void resetPassword(AppResetPasswordReq req) {
        ValidationUtils.throwIfNull(req, "请填写重置密码信息");
        String phone = StrUtil.trim(req.getPhone());
        this.verifyAppSmsCode(phone, req.getCode());
        AppUserDO user = appUserService.findByPhone(phone);
        ValidationUtils.throwIfNull(user, "该手机号未注册");
        appUserService.updatePassword(user.getId(), req.getNewPassword());
        RedisUtils.delete(AppAuthConstants.appPasswordErrorKey(phone));
        // 重置密码后全部会话失效，需用新密码重新登录
        this.kickOutTokens(user.getId(), null);
    }

    /**
     * 手机号验证码登录
     *
     * @param req 登录请求参数
     * @return 登录主体
     */
    AppLoginUser phoneLogin(AppPhoneLoginReq req) {
        String phone = StrUtil.trim(req.getPhone());
        this.checkGraphicCaptcha(phone, req);
        try {
            this.verifyAppSmsCode(phone, req.getCode());
        } catch (BaseException e) {
            this.increaseSmsCodeErrorCount(phone);
            throw e;
        }
        RedisUtils.delete(AppAuthConstants.appSmsCodeErrorKey(phone));
        return appUserService.obtainOrRegisterByPhone(phone);
    }

    /**
     * 账号密码登录
     *
     * @param req     登录请求参数
     * @param request 请求对象
     * @return 登录主体
     */
    AppLoginUser accountLogin(AppAccountLoginReq req, HttpServletRequest request) {
        String account = StrUtil.trim(req.getAccount());
        // 一期 C 端无独立用户名列，登录账号即已绑定手机号
        AppUserDO user = appUserService.findByPhone(account);
        boolean isPasswordMatched = user != null && StrUtil.isNotBlank(user.getPassword()) && passwordEncoder
            .matches(req.getPassword(), user.getPassword());
        this.checkPasswordError(account, isPasswordMatched);
        ValidationUtils.throwIfNull(user, LOGIN_ERROR);
        return new AppLoginUser(user, false);
    }

    /**
     * 校验 C 端短信验证码（CAPTCHA:app:{phone}，一次性消费）
     *
     * @param phone 手机号
     * @param code  待校验验证码
     */
    void verifyAppSmsCode(String phone, String code) {
        ValidationUtils.throwIfBlank(phone, "手机号不能为空");
        ValidationUtils.throwIfBlank(code, "验证码不能为空");
        String captchaKey = AppAuthConstants.appSmsCodeKey(phone);
        String cachedCode = RedisUtils.get(captchaKey);
        ValidationUtils.throwIfBlank(cachedCode, CAPTCHA_EXPIRED);
        // 无论校验成功与否，验证码均立即失效（防重放）
        RedisUtils.delete(captchaKey);
        ValidationUtils.throwIfNotEqualIgnoreCase(StrUtil.trim(code), cachedCode, CAPTCHA_ERROR);
    }

    /**
     * 密码错误计数与锁定判定（连续 5 次错误锁定 15 分钟）
     *
     * @param account           登录账号
     * @param isPasswordMatched 密码是否正确
     */
    void checkPasswordError(String account, boolean isPasswordMatched) {
        String key = AppAuthConstants.appPasswordErrorKey(account);
        Integer cacheErrorCount = RedisUtils.get(key);
        int errorCount = ObjectUtil.defaultIfNull(cacheErrorCount, 0);
        // 已锁定：即使密码正确也不签发令牌
        CheckUtils
            .throwIf(errorCount >= AppAuthConstants.MAX_PASSWORD_ERROR_COUNT, PasswordPolicyEnum.PASSWORD_ERROR_LOCK_MINUTES
                .getMsg()
                .formatted(this.getUnlockTime(key)));
        if (isPasswordMatched) {
            RedisUtils.delete(key);
            return;
        }
        errorCount++;
        RedisUtils.set(key, errorCount, Duration.ofMinutes(AppAuthConstants.PASSWORD_ERROR_LOCK_MINUTES));
        CheckUtils
            .throwIf(errorCount >= AppAuthConstants.MAX_PASSWORD_ERROR_COUNT, PasswordPolicyEnum.PASSWORD_ERROR_LOCK_COUNT
                .getMsg()
                .formatted(AppAuthConstants.MAX_PASSWORD_ERROR_COUNT, AppAuthConstants.PASSWORD_ERROR_LOCK_MINUTES, this
                    .getUnlockTime(key)));
        throw new BusinessException(LOGIN_ERROR);
    }

    /**
     * 短信验证码连续错误达到阈值后要求行为（图形）验证码
     *
     * @param phone 手机号
     * @param req   登录请求参数
     */
    void checkGraphicCaptcha(String phone, AppPhoneLoginReq req) {
        Integer cacheErrorCount = RedisUtils.get(AppAuthConstants.appSmsCodeErrorKey(phone));
        int errorCount = ObjectUtil.defaultIfNull(cacheErrorCount, 0);
        if (errorCount < AppAuthConstants.SMS_CODE_ERROR_CAPTCHA_THRESHOLD) {
            return;
        }
        ValidationUtils.throwIfBlank(req.getCaptchaId(), "请输入图形验证码");
        ValidationUtils.throwIfBlank(req.getCaptchaValue(), "请输入图形验证码");
        String captchaKey = CacheConstants.CAPTCHA_KEY_PREFIX + req.getCaptchaId();
        String captcha = RedisUtils.get(captchaKey);
        RedisUtils.delete(captchaKey);
        ValidationUtils.throwIfBlank(captcha, CAPTCHA_EXPIRED);
        ValidationUtils.throwIfNotEqualIgnoreCase(req.getCaptchaValue(), captcha, "图形验证码不正确");
    }

    /**
     * 递增短信验证码错误计数
     *
     * @param phone 手机号
     */
    void increaseSmsCodeErrorCount(String phone) {
        String key = AppAuthConstants.appSmsCodeErrorKey(phone);
        Integer cacheErrorCount = RedisUtils.get(key);
        int errorCount = ObjectUtil.defaultIfNull(cacheErrorCount, 0);
        RedisUtils.set(key, errorCount + 1, Duration.ofMinutes(AppAuthConstants.SMS_CODE_ERROR_MINUTES));
    }

    /**
     * 校验 App 客户端配置
     *
     * @param authType 认证类型
     * @return 客户端配置
     */
    ClientResp getAppClient(AuthTypeEnum authType) {
        ClientResp client = clientService.getByClientId(AppAuthConstants.APP_CLIENT_ID);
        ValidationUtils.throwIfNull(client, "客户端不存在");
        ValidationUtils.throwIf(DisEnableStatusEnum.DISABLE.equals(client.getStatus()), "客户端已禁用");
        ValidationUtils.throwIf(client.getAuthType() == null || !client.getAuthType()
            .contains(authType.getValue()), "该客户端暂未授权 [{}] 认证", authType.getDescription());
        return client;
    }

    /**
     * 校验账号状态
     *
     * @param user 用户实体
     */
    void checkAccountStatus(AppUserDO user) {
        Integer accountStatus = user.getAccountStatus();
        CheckUtils.throwIf(AccountStatusEnum.LOCKED.getValue().equals(accountStatus), "账号已被锁定，请稍后再试");
        CheckUtils.throwIf(AccountStatusEnum.CANCELLED.getValue().equals(accountStatus), "账号已注销");
    }

    /**
     * 执行认证（登录参数全部取自 sys_client 的 APP 行，形态与后台 AbstractLoginHandler 一致）
     *
     * @param user     用户实体
     * @param client   客户端配置
     * @param deviceId 设备标识（可为 null）
     * @return 令牌
     */
    String authenticate(AppUserDO user, ClientResp client, String deviceId) {
        SaLoginParameter loginParameter = new SaLoginParameter();
        if (client.getActiveTimeout() != null) {
            loginParameter.setActiveTimeout(client.getActiveTimeout());
        }
        if (client.getTimeout() != null) {
            loginParameter.setTimeout(client.getTimeout());
        }
        loginParameter.setDeviceType(client.getClientType());
        loginParameter.setExtra(CLIENT_ID, client.getClientId());
        if (StrUtil.isNotBlank(deviceId)) {
            // 以设备标识为设备维度，同一设备重复登录不占用新的设备额度
            loginParameter.setDevice(StrUtil.trim(deviceId));
        }
        if (client.getIsConcurrent() != null) {
            loginParameter.setIsConcurrent(client.getIsConcurrent());
            if (Boolean.FALSE.equals(client.getIsConcurrent()) && client.getReplacedRange() != null) {
                loginParameter.setReplacedRange(SaReplacedRange.valueOf(client.getReplacedRange().getValue()));
            }
        }
        Integer maxLoginCount = client.getMaxLoginCount();
        if (maxLoginCount != null) {
            loginParameter.setMaxLoginCount(maxLoginCount);
            if (maxLoginCount != -1 && client.getOverflowLogoutMode() != null) {
                loginParameter.setOverflowLogoutMode(SaLogoutMode.valueOf(client.getOverflowLogoutMode().getValue()));
            }
        }
        loginParameter.setExtraData(BeanUtil.beanToMap(new UserExtraContext(ServletUtils.getRequest())));
        StpAppUtil.login(user.getId(), loginParameter);
        String token = StrUtil.blankToDefault(StpAppUtil.getTokenValue(), ExceptionUtils.exToNull(() -> StpAppUtil
            .stpLogic()
            .getTokenInfo()
            .getTokenValue()));
        ValidationUtils.throwIfBlank(token, "登录失败，请稍后重试");
        AppUserContext context = appUserService.buildContext(user);
        context.setClientId(client.getClientId());
        context.setClientType(client.getClientType());
        AppUserContextHolder.setContext(context);
        // 以本次签发的 token 值为键缓存上下文，避免受登录域令牌名拼接规则影响而每次请求回源查库
        ExceptionUtils.exToNull(() -> StpAppUtil.stpLogic()
            .getTokenSessionByToken(token)
            .set(AppUserContextHolder.CONTEXT_KEY, context));
        return token;
    }

    /**
     * 发送短信验证码（抽取为独立方法，便于单测隔离短信通道）
     *
     * @param phone               手机号
     * @param code                验证码
     * @param expirationInMinutes 有效期（分钟）
     */
    void doSendSmsCode(String phone, String code, long expirationInMinutes) {
        CaptchaProperties.CaptchaSms captchaSms = captchaProperties.getSms();
        SmsConfigDO smsConfig = smsConfigService.getDefaultConfig();
        SmsBlend smsBlend;
        try {
            smsBlend = smsConfig == null
                ? SmsFactory.getSmsBlend()
                : SmsFactory.getSmsBlend(String.valueOf(smsConfig.getId()));
        } catch (Exception e) {
            log.error("未获取到可用的短信通道配置", e);
            throw new BusinessException("短信通道未配置，请联系管理员");
        }
        ValidationUtils.throwIfNull(smsBlend, "短信通道未配置，请联系管理员");
        LinkedHashMap<String, String> messageMap = new LinkedHashMap<>(2);
        messageMap.put(captchaSms.getCodeKey(), code);
        messageMap.put(captchaSms.getTimeKey(), String.valueOf(expirationInMinutes));
        SmsResponse smsResponse;
        try {
            smsResponse = smsBlend.sendMessage(phone, messageMap);
        } catch (Exception e) {
            log.error("发送短信验证码失败，手机号 [{}]", AppUserServiceImpl.maskPhone(phone), e);
            throw new BusinessException("验证码发送失败，请稍后再试");
        }
        CheckUtils.throwIf(smsResponse == null || !smsResponse.isSuccess(), "验证码发送失败，请稍后再试");
    }

    /**
     * 短信验证码场景校验
     *
     * @param phone 手机号
     * @param scene 场景
     */
    void checkSmsCodeScene(String phone, AppSmsSceneEnum scene) {
        ValidationUtils.throwIfBlank(phone, "手机号不能为空");
        ValidationUtils.throwIfNull(scene, "验证码场景不能为空");
        switch (scene) {
            case RESET -> ValidationUtils.throwIfNull(appUserService.findByPhone(phone), "该手机号未注册");
            case BIND -> {
                AppUserDO existUser = appUserService.findByPhone(phone);
                if (existUser != null) {
                    ValidationUtils.throwIf(!Objects.equals(existUser.getId(), this
                        .getCurrentUserIdOrNull()), "该手机号已绑定其他账号");
                }
            }
            // LOGIN：未注册手机号允许下发验证码（登录时自动建档）
            default -> {
            }
        }
    }

    /**
     * 踢除该用户除指定令牌外的其他会话
     *
     * @param userId    用户 ID
     * @param keepToken 需保留的令牌（可为 null，表示全部下线）
     */
    void kickOutOtherTokens(Long userId, String keepToken) {
        String currentToken = keepToken;
        if (StrUtil.isBlank(currentToken)) {
            currentToken = ExceptionUtils.exToNull(StpAppUtil::getTokenValue);
        }
        this.kickOutTokens(userId, currentToken);
    }

    /**
     * 下线指定令牌集合（keepToken 为 null 时下线该用户全部令牌）
     *
     * @param userId    用户 ID
     * @param keepToken 需保留的令牌
     */
    void kickOutTokens(Long userId, String keepToken) {
        try {
            for (String tokenValue : StpAppUtil.stpLogic().getTokenValueListByLoginId(userId)) {
                if (Objects.equals(tokenValue, keepToken)) {
                    continue;
                }
                StpAppUtil.logoutByToken(tokenValue);
            }
        } catch (Exception e) {
            log.warn("下线 C 端其他会话失败（不影响本次操作结果），userId={}：{}", userId, e.getMessage());
        }
    }

    private Long getCurrentUserIdOrNull() {
        try {
            return AppUserContextHolder.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private String getUnlockTime(String key) {
        long timeToLive = RedisUtils.getTimeToLive(key);
        return timeToLive > 0
            ? DateUtil.date()
                .offset(DateField.MILLISECOND, (int)timeToLive)
                .toString(DatePattern.CHINESE_DATE_TIME_FORMAT)
            : LocalDateTime.now().toString();
    }

    private int getSmsCodeLength() {
        CaptchaProperties.CaptchaSms captchaSms = captchaProperties.getSms();
        return captchaSms == null || captchaSms.getLength() <= 0 ? DEFAULT_SMS_CODE_LENGTH : captchaSms.getLength();
    }

    private long getSmsCodeExpirationInMinutes() {
        CaptchaProperties.CaptchaSms captchaSms = captchaProperties.getSms();
        return captchaSms == null || captchaSms.getExpirationInMinutes() <= 0
            ? DEFAULT_SMS_CODE_MINUTES
            : captchaSms.getExpirationInMinutes();
    }
}
