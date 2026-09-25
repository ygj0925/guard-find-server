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

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.DesensitizedUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import com.guardfind.server.app.constant.AppAuthConstants;
import com.guardfind.server.app.context.AppUserContext;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.enums.AccountStatusEnum;
import com.guardfind.server.app.enums.IdentityTypeEnum;
import com.guardfind.server.app.mapper.AppDeviceMapper;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.app.model.AppLoginUser;
import com.guardfind.server.app.model.entity.AppDeviceDO;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.app.model.req.AppDeactivateReq;
import com.guardfind.server.app.model.req.AppLoginReq;
import com.guardfind.server.app.model.req.AppProfileReq;
import com.guardfind.server.app.model.resp.AppDeactivateResp;
import com.guardfind.server.app.model.resp.AppUserResp;
import com.guardfind.server.app.service.AppUserService;
import com.guardfind.server.common.enums.GenderEnum;
import com.guardfind.server.notification.service.AppMessageService;
import top.continew.starter.core.util.validation.CheckUtils;
import top.continew.starter.core.util.validation.ValidationUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * C 端用户业务实现
 *
 * <p>
 * 写库说明：C 端无后台登录态，{@code MyBatisPlusMetaObjectHandler} 仅按后台上下文填充 create_user/update_user，
 * 因此本类所有更新均显式写入 C 端用户 ID 与时间；phone / password 等加密列由字段加密拦截器在写库时加密
 * （password 走 {@code PasswordEncoderEncryptor}，即 BCRYPT）。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppUserServiceImpl implements AppUserService {

    private final AppUserMapper appUserMapper;
    private final AppDeviceMapper appDeviceMapper;
    private final AppMessageService appMessageService;

    @Override
    public AppUserDO getById(Long id) {
        ValidationUtils.throwIfNull(id, "请先登录");
        AppUserDO user = appUserMapper.selectById(id);
        ValidationUtils.throwIfNull(user, "用户不存在");
        return user;
    }

    @Override
    public AppUserDO findByPhone(String phone) {
        return StrUtil.isBlank(phone) ? null : appUserMapper.selectByPhone(phone);
    }

    @Override
    public AppLoginUser obtainOrRegisterByPhone(String phone) {
        ValidationUtils.throwIfBlank(phone, "手机号不能为空");
        AppUserDO user = this.findByPhone(phone);
        if (user != null) {
            return new AppLoginUser(user, false);
        }
        AppUserDO register = new AppUserDO();
        register.setNickname(this.generateDefaultNickname());
        register.setGender(GenderEnum.UNKNOWN);
        register.setPhone(phone);
        register.setAccountStatus(AccountStatusEnum.NORMAL.getValue());
        register.setIdentityType(IdentityTypeEnum.PUBLIC.name());
        register.setRealNameStatus(AppAuthConstants.DEFAULT_REAL_NAME_STATUS);
        register.setVolunteerVerified(false);
        register.setRegistrationSource(AppAuthConstants.DEFAULT_REGISTRATION_SOURCE);
        try {
            appUserMapper.insert(register);
        } catch (DuplicateKeyException e) {
            // 同一手机号并发首登：uk_phone 冲突时以已建档数据为准，保证建档幂等
            log.warn("手机号 [{}] 并发建档冲突，改用已存在档案", phone);
            AppUserDO existUser = this.findByPhone(phone);
            ValidationUtils.throwIfNull(existUser, "账号创建失败，请稍后再试");
            return new AppLoginUser(existUser, false);
        }
        return new AppLoginUser(register, true);
    }

    @Override
    public AppUserResp getResp(Long id) {
        return this.toResp(this.getById(id));
    }

    @Override
    public AppUserResp updateProfile(Long id, AppProfileReq req) {
        AppUserDO user = this.getById(id);
        this.checkModifiable(user);
        ValidationUtils.throwIfNull(req, "请提交要修改的资料");
        GenderEnum gender = this.parseGender(req.getGender());
        LambdaUpdateChainWrapper<AppUserDO> wrapper = appUserMapper.lambdaUpdate().eq(AppUserDO::getId, id);
        boolean isUpdate = false;
        if (StrUtil.isNotBlank(req.getNickname())) {
            wrapper.set(AppUserDO::getNickname, StrUtil.trim(req.getNickname()));
            isUpdate = true;
        }
        if (StrUtil.isNotBlank(req.getAvatar())) {
            wrapper.set(AppUserDO::getAvatar, StrUtil.trim(req.getAvatar()));
            isUpdate = true;
        }
        if (gender != null) {
            wrapper.set(AppUserDO::getGender, gender);
            isUpdate = true;
        }
        if (req.getIdentityType() != null) {
            wrapper.set(AppUserDO::getIdentityType, req.getIdentityType().name());
            isUpdate = true;
        }
        if (req.getProvinceCode() != null) {
            wrapper.set(AppUserDO::getProvinceCode, StrUtil.trimToNull(req.getProvinceCode()));
            isUpdate = true;
        }
        if (req.getCityCode() != null) {
            wrapper.set(AppUserDO::getCityCode, StrUtil.trimToNull(req.getCityCode()));
            isUpdate = true;
        }
        if (req.getDistrictCode() != null) {
            wrapper.set(AppUserDO::getDistrictCode, StrUtil.trimToNull(req.getDistrictCode()));
            isUpdate = true;
        }
        if (isUpdate) {
            wrapper.set(AppUserDO::getUpdateUser, id);
            wrapper.set(AppUserDO::getUpdateTime, LocalDateTime.now());
            wrapper.update();
            // 昵称、身份类型被下游（线索可见性、志愿者判定）缓存于 token session，需同步刷新
            this.refreshContext(id);
        }
        return this.getResp(id);
    }

    @Override
    public void updatePassword(Long id, String password) {
        AppUserDO user = this.getById(id);
        this.checkModifiable(user);
        ValidationUtils.throwIfBlank(password, "新密码不能为空");
        appUserMapper.lambdaUpdate()
            .set(AppUserDO::getPassword, password)
            .set(AppUserDO::getUpdateUser, id)
            .set(AppUserDO::getUpdateTime, LocalDateTime.now())
            .eq(AppUserDO::getId, id)
            .update();
    }

    @Override
    public void bindPhone(Long id, String phone) {
        AppUserDO user = this.getById(id);
        this.checkModifiable(user);
        ValidationUtils.throwIfBlank(phone, "手机号不能为空");
        CheckUtils.throwIfEqual(phone, user.getPhone(), "新手机号不能与当前手机号相同");
        CheckUtils.throwIfNotNull(this.findByPhone(phone), "该手机号已绑定其他账号");
        appUserMapper.lambdaUpdate()
            .set(AppUserDO::getPhone, phone)
            .set(AppUserDO::getUpdateUser, id)
            .set(AppUserDO::getUpdateTime, LocalDateTime.now())
            .eq(AppUserDO::getId, id)
            .update();
    }

    @Override
    public AppDeactivateResp deactivate(Long id, AppDeactivateReq req) {
        AppUserDO user = this.getById(id);
        ValidationUtils.throwIf(req == null || StrUtil.isBlank(req.getReason()), "注销原因不能为空");
        Integer accountStatus = user.getAccountStatus();
        CheckUtils.throwIf(AccountStatusEnum.CANCELLED.getValue().equals(accountStatus), "账号已注销");
        CheckUtils.throwIf(AccountStatusEnum.CANCELLING.getValue().equals(accountStatus), "注销申请已提交，冷静期内重新登录即可撤销");
        LocalDateTime now = LocalDateTime.now();
        appUserMapper.lambdaUpdate()
            .set(AppUserDO::getAccountStatus, AccountStatusEnum.CANCELLING.getValue())
            .set(AppUserDO::getDeactivateApplyTime, now)
            .set(AppUserDO::getUpdateUser, id)
            .set(AppUserDO::getUpdateTime, now)
            .eq(AppUserDO::getId, id)
            .update();
        return this.buildDeactivateResp(now);
    }

    @Override
    public boolean cancelDeactivationIfCancelling(AppUserDO user) {
        if (user == null || !AccountStatusEnum.CANCELLING.getValue().equals(user.getAccountStatus())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        // 冷静期内登录即撤销注销：状态回到 NORMAL、清空注销申请时间，业务数据完整保留
        appUserMapper.lambdaUpdate()
            .set(AppUserDO::getAccountStatus, AccountStatusEnum.NORMAL.getValue())
            .set(AppUserDO::getDeactivateApplyTime, null)
            .set(AppUserDO::getUpdateUser, user.getId())
            .set(AppUserDO::getUpdateTime, now)
            .eq(AppUserDO::getId, user.getId())
            .update();
        user.setAccountStatus(AccountStatusEnum.NORMAL.getValue());
        user.setDeactivateApplyTime(null);
        return true;
    }

    @Override
    public void afterLogin(AppUserDO user, AppLoginReq loginReq) {
        LocalDateTime now = LocalDateTime.now();
        appUserMapper.lambdaUpdate()
            .set(AppUserDO::getLastActiveTime, now)
            .set(AppUserDO::getUpdateUser, user.getId())
            .set(AppUserDO::getUpdateTime, now)
            .eq(AppUserDO::getId, user.getId())
            .update();
        this.upsertDevice(user.getId(), loginReq, now);
    }

    @Override
    public AppUserContext buildContext(AppUserDO user) {
        AppUserContext context = new AppUserContext();
        context.setId(user.getId());
        context.setNickname(user.getNickname());
        context.setIdentityType(StrUtil.blankToDefault(user.getIdentityType(), IdentityTypeEnum.PUBLIC.name()));
        context.setCityCode(user.getCityCode());
        return context;
    }

    /**
     * 构建响应参数（手机号在此处脱敏，不依赖序列化注解）
     *
     * @param user 用户实体
     * @return 用户信息
     */
    AppUserResp toResp(AppUserDO user) {
        ValidationUtils.throwIfNull(user, "用户不存在");
        AppUserResp resp = new AppUserResp();
        resp.setId(user.getId());
        resp.setNickname(user.getNickname());
        resp.setAvatar(user.getAvatar());
        resp.setGender((user.getGender() == null ? GenderEnum.UNKNOWN : user.getGender()).name());
        resp.setPhone(maskPhone(user.getPhone()));
        resp.setBoundPhone(StrUtil.isNotBlank(user.getPhone()));
        resp.setIdentityType(StrUtil.blankToDefault(user.getIdentityType(), IdentityTypeEnum.PUBLIC.name()));
        resp.setRealNameStatus(StrUtil.blankToDefault(user
            .getRealNameStatus(), AppAuthConstants.DEFAULT_REAL_NAME_STATUS));
        resp.setProvinceCode(user.getProvinceCode());
        resp.setCityCode(user.getCityCode());
        resp.setDistrictCode(user.getDistrictCode());
        resp.setVolunteerVerified(Boolean.TRUE.equals(user.getVolunteerVerified()));
        resp.setHasPassword(StrUtil.isNotBlank(user.getPassword()));
        resp.setUnreadCount(user.getId() == null ? 0L : appMessageService.countUnread(user.getId()));
        resp.setCreatedAt(user.getCreateTime());
        return resp;
    }

    /**
     * 手机号脱敏（138****1234）
     *
     * @param phone 手机号明文
     * @return 脱敏后的手机号
     */
    static String maskPhone(String phone) {
        return StrUtil.isBlank(phone) ? null : DesensitizedUtil.mobilePhone(phone);
    }

    /**
     * 生成默认昵称：无脚星友 + 4 位随机数字
     *
     * @return 默认昵称
     */
    static String generateDefaultNickname() {
        return AppAuthConstants.DEFAULT_NICKNAME_PREFIX + RandomUtil
            .randomNumbers(AppAuthConstants.NICKNAME_RANDOM_LENGTH);
    }

    /**
     * 构造注销冷静期响应
     *
     * @param applyTime 注销申请时间
     * @return 冷静期信息
     */
    static AppDeactivateResp buildDeactivateResp(LocalDateTime applyTime) {
        AppDeactivateResp resp = new AppDeactivateResp();
        resp.setCooldownDays(AppAuthConstants.DEACTIVATE_COOLDOWN_DAYS);
        resp.setDeadline(applyTime.plusDays(AppAuthConstants.DEACTIVATE_COOLDOWN_DAYS));
        return resp;
    }

    /**
     * 登记/更新登录设备（尽力而为，失败不阻断登录）
     *
     * @param userId    用户 ID
     * @param loginReq  登录请求参数
     * @param loginTime 登录时间
     */
    void upsertDevice(Long userId, AppLoginReq loginReq, LocalDateTime loginTime) {
        if (loginReq == null || StrUtil.isBlank(loginReq.getDeviceId())) {
            return;
        }
        String deviceId = StrUtil.trim(loginReq.getDeviceId());
        String platform = StrUtil.blankToDefault(StrUtil.trim(loginReq
            .getPlatform()), AppAuthConstants.DEFAULT_PLATFORM);
        try {
            List<AppDeviceDO> devices = appDeviceMapper.lambdaQuery()
                .eq(AppDeviceDO::getUserId, userId)
                .eq(AppDeviceDO::getDeviceId, deviceId)
                .list();
            AppDeviceDO device = CollUtil.getFirst(devices);
            if (device == null) {
                AppDeviceDO insertDevice = new AppDeviceDO();
                insertDevice.setUserId(userId);
                insertDevice.setPlatform(platform);
                insertDevice.setDeviceId(deviceId);
                insertDevice.setDeviceName(StrUtil.trim(loginReq.getDeviceName()));
                insertDevice.setAppVersion(StrUtil.trim(loginReq.getAppVersion()));
                insertDevice.setOsVersion(StrUtil.trim(loginReq.getOsVersion()));
                insertDevice.setLastLoginTime(loginTime);
                insertDevice.setCreateUser(userId);
                insertDevice.setUpdateUser(userId);
                appDeviceMapper.insert(insertDevice);
                return;
            }
            LambdaUpdateChainWrapper<AppDeviceDO> wrapper = appDeviceMapper.lambdaUpdate()
                .eq(AppDeviceDO::getId, device.getId());
            wrapper.set(AppDeviceDO::getPlatform, platform);
            wrapper.set(AppDeviceDO::getLastLoginTime, loginTime);
            if (StrUtil.isNotBlank(loginReq.getDeviceName())) {
                wrapper.set(AppDeviceDO::getDeviceName, StrUtil.trim(loginReq.getDeviceName()));
            }
            if (StrUtil.isNotBlank(loginReq.getAppVersion())) {
                wrapper.set(AppDeviceDO::getAppVersion, StrUtil.trim(loginReq.getAppVersion()));
            }
            if (StrUtil.isNotBlank(loginReq.getOsVersion())) {
                wrapper.set(AppDeviceDO::getOsVersion, StrUtil.trim(loginReq.getOsVersion()));
            }
            wrapper.set(AppDeviceDO::getUpdateUser, userId);
            wrapper.set(AppDeviceDO::getUpdateTime, loginTime);
            wrapper.update();
        } catch (Exception e) {
            log.warn("登记 App 设备信息失败（不影响登录），userId={}，deviceId={}", userId, deviceId, e);
        }
    }

    /**
     * 解析性别
     *
     * @param gender 性别字符串
     * @return 性别枚举，入参为空时返回 null
     */
    static GenderEnum parseGender(String gender) {
        if (StrUtil.isBlank(gender)) {
            return null;
        }
        GenderEnum matchedGender = Arrays.stream(GenderEnum.values())
            .filter(item -> item.name().equalsIgnoreCase(StrUtil.trim(gender)))
            .findFirst()
            .orElse(null);
        ValidationUtils.throwIfNull(matchedGender, "性别取值无效");
        return matchedGender;
    }

    /**
     * 校验账号是否允许变更资料（锁定/已注销不允许）
     *
     * @param user 用户实体
     */
    private void checkModifiable(AppUserDO user) {
        Integer accountStatus = user.getAccountStatus();
        CheckUtils.throwIf(AccountStatusEnum.CANCELLED.getValue().equals(accountStatus), "账号已注销");
        CheckUtils.throwIf(AccountStatusEnum.LOCKED.getValue().equals(accountStatus), "账号已被锁定，请稍后再试");
    }

    /**
     * 刷新 token session 中缓存的 C 端上下文（无登录态时忽略）
     *
     * @param userId 用户 ID
     */
    private void refreshContext(Long userId) {
        try {
            AppUserContext currentContext = AppUserContextHolder.getContext();
            if (currentContext == null) {
                return;
            }
            AppUserDO user = appUserMapper.selectById(userId);
            ValidationUtils.throwIfNull(user, "用户不存在");
            AppUserContext context = this.buildContext(user);
            context.setClientId(currentContext.getClientId());
            context.setClientType(currentContext.getClientType());
            AppUserContextHolder.setContext(context);
        } catch (Exception e) {
            log.warn("刷新 C 端用户上下文失败（不影响本次请求结果），userId={}：{}", userId, e.getMessage());
        }
    }
}
