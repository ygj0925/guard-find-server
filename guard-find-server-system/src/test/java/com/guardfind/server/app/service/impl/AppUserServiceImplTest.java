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

import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.guardfind.server.app.context.AppUserContext;
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
import com.guardfind.server.common.enums.GenderEnum;
import com.guardfind.server.notification.service.AppMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import top.continew.starter.core.exception.BadRequestException;
import top.continew.starter.core.exception.BusinessException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C 端用户业务单元测试（不加载 Spring 上下文，不依赖 MySQL/Redis）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@ExtendWith(MockitoExtension.class)
class AppUserServiceImplTest {

    private static final String PHONE = "13800001234";
    private static final Long USER_ID = 1789000000000000001L;

    @Mock
    private AppUserMapper appUserMapper;
    @Mock
    private AppDeviceMapper appDeviceMapper;
    @Mock
    private AppMessageService appMessageService;

    private AppUserServiceImpl appUserService;

    @SuppressWarnings("unchecked")
    private final LambdaUpdateChainWrapper<AppUserDO> updateChainWrapper = mock(LambdaUpdateChainWrapper.class);

    @BeforeEach
    void setUp() {
        appUserService = new AppUserServiceImpl(appUserMapper, appDeviceMapper, appMessageService);
        // MyBatis-Plus 链式更新为 default 方法，测试中以宽松桩返回自身，保证 set/eq 可连续调用
        lenient().when(updateChainWrapper.set(any(), any())).thenReturn(updateChainWrapper);
        lenient().when(updateChainWrapper.eq(any(), any())).thenReturn(updateChainWrapper);
        lenient().when(updateChainWrapper.update()).thenReturn(true);
    }

    @Test
    @DisplayName("默认昵称格式为 无脚星友 + 4 位数字")
    void defaultNicknameShouldBePrefixWithFourDigits() {
        for (int i = 0; i < 200; i++) {
            assertThat(AppUserServiceImpl.generateDefaultNickname()).matches("^无脚星友\\d{4}$");
        }
    }

    @Test
    @DisplayName("首次验证码登录：自动建档（正常状态、默认昵称、手机号以明文入参交由加密列落库）")
    void obtainOrRegisterByPhoneShouldRegisterNewUser() {
        when(appUserMapper.selectByPhone(PHONE)).thenReturn(null);
        AppLoginUser loginUser = appUserService.obtainOrRegisterByPhone(PHONE);
        ArgumentCaptor<AppUserDO> captor = ArgumentCaptor.forClass(AppUserDO.class);
        verify(appUserMapper).insert(captor.capture());
        AppUserDO registered = captor.getValue();
        assertThat(loginUser.isNewUser()).isTrue();
        assertThat(registered.getNickname()).matches("^无脚星友\\d{4}$");
        assertThat(registered.getPhone()).isEqualTo(PHONE);
        assertThat(registered.getAccountStatus()).isEqualTo(1);
        assertThat(registered.getIdentityType()).isEqualTo("PUBLIC");
        assertThat(registered.getRealNameStatus()).isEqualTo("UNCHECKED");
        assertThat(registered.getRegistrationSource()).isEqualTo("APP");
        assertThat(registered.getVolunteerVerified()).isFalse();
    }

    @Test
    @DisplayName("自动建档幂等：同一手机号再次登录不重复建档")
    void obtainOrRegisterByPhoneShouldBeIdempotent() {
        AppUserDO existUser = buildUser(1);
        when(appUserMapper.selectByPhone(PHONE)).thenReturn(existUser);
        AppLoginUser first = appUserService.obtainOrRegisterByPhone(PHONE);
        AppLoginUser second = appUserService.obtainOrRegisterByPhone(PHONE);
        assertThat(first.isNewUser()).isFalse();
        assertThat(second.isNewUser()).isFalse();
        assertThat(second.getUser()).isSameAs(existUser);
        verify(appUserMapper, never()).insert(any(AppUserDO.class));
    }

    @Test
    @DisplayName("自动建档幂等：并发唯一键冲突时复用已建档案")
    void obtainOrRegisterByPhoneShouldReuseUserOnDuplicateKey() {
        AppUserDO existUser = buildUser(1);
        when(appUserMapper.selectByPhone(PHONE)).thenReturn(null).thenReturn(existUser);
        when(appUserMapper.insert(any(AppUserDO.class)))
            .thenThrow(new DuplicateKeyException("Duplicate entry for key uk_phone"));
        AppLoginUser loginUser = appUserService.obtainOrRegisterByPhone(PHONE);
        assertThat(loginUser.isNewUser()).isFalse();
        assertThat(loginUser.getUser()).isSameAs(existUser);
    }

    @Test
    @DisplayName("响应参数：手机号脱敏返回，未读数为消息模块计数")
    void toRespShouldMaskPhone() {
        AppUserDO user = buildUser(1);
        user.setGender(GenderEnum.MALE);
        user.setPassword("$2a$10$encrypted");
        user.setIdentityType("VOLUNTEER");
        user.setCreateTime(LocalDateTime.of(2026, 9, 24, 10, 0, 0));
        when(appMessageService.countUnread(USER_ID)).thenReturn(3L);
        AppUserResp resp = appUserService.toResp(user);
        assertThat(resp.getPhone()).isEqualTo("138****1234");
        assertThat(resp.getBoundPhone()).isTrue();
        assertThat(resp.getHasPassword()).isTrue();
        assertThat(resp.getGender()).isEqualTo("MALE");
        assertThat(resp.getIdentityType()).isEqualTo("VOLUNTEER");
        assertThat(resp.getUnreadCount()).isEqualTo(3L);
        assertThat(resp.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 24, 10, 0, 0));
    }

    @Test
    @DisplayName("响应参数：未绑定手机号时 phone 为空、boundPhone 为 false")
    void toRespShouldHandleUnboundPhone() {
        AppUserDO user = buildUser(1);
        user.setPhone(null);
        user.setGender(null);
        when(appMessageService.countUnread(USER_ID)).thenReturn(0L);
        AppUserResp resp = appUserService.toResp(user);
        assertThat(resp.getPhone()).isNull();
        assertThat(resp.getBoundPhone()).isFalse();
        assertThat(resp.getHasPassword()).isFalse();
        assertThat(resp.getGender()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("查询用户：不存在时抛出业务异常")
    void getByIdShouldFailWhenUserNotFound() {
        when(appUserMapper.selectById(USER_ID)).thenReturn(null);
        assertThatExceptionOfType(BadRequestException.class).isThrownBy(() -> appUserService.getById(USER_ID))
            .withMessage("用户不存在");
    }

    @Test
    @DisplayName("申请注销：状态转为 CANCELLING 并返回 15 日冷静期截止时间")
    void deactivateShouldApplyCooldown() {
        LocalDateTime now = LocalDateTime.now();
        when(appUserMapper.selectById(USER_ID)).thenReturn(buildUser(1));
        stubUpdateChain();
        AppDeactivateResp resp = appUserService.deactivate(USER_ID, buildDeactivateReq());
        assertThat(resp.getCooldownDays()).isEqualTo(15);
        assertThat(resp.getDeadline()).isBetween(now.plusDays(14), now.plusDays(16));
        verify(appUserMapper).lambdaUpdate();
        verify(updateChainWrapper).update();
    }

    @Test
    @DisplayName("申请注销：冷静期内重复提交被拒绝")
    void deactivateShouldRejectDuplicatedApply() {
        when(appUserMapper.selectById(USER_ID)).thenReturn(buildUser(3));
        assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> appUserService
            .deactivate(USER_ID, buildDeactivateReq())).withMessageContaining("注销申请已提交");
        verify(appUserMapper, never()).lambdaUpdate();
    }

    @Test
    @DisplayName("冷静期内登录：状态回到 NORMAL（cancel-on-login）")
    void cancelDeactivationShouldRestoreNormalStatus() {
        AppUserDO user = buildUser(3);
        user.setDeactivateApplyTime(LocalDateTime.now());
        stubUpdateChain();
        assertThat(appUserService.cancelDeactivationIfCancelling(user)).isTrue();
        assertThat(user.getAccountStatus()).isEqualTo(1);
        assertThat(user.getDeactivateApplyTime()).isNull();
        verify(updateChainWrapper).update();
    }

    @Test
    @DisplayName("非注销冷静期用户登录：不触发撤销逻辑")
    void cancelDeactivationShouldSkipNormalUser() {
        assertThat(appUserService.cancelDeactivationIfCancelling(buildUser(1))).isFalse();
        verify(appUserMapper, never()).lambdaUpdate();
    }

    @Test
    @DisplayName("资料更新：仅提交昵称与性别时按提交值更新")
    void updateProfileShouldOnlyUpdateSubmittedFields() {
        when(appUserMapper.selectById(USER_ID)).thenReturn(buildUser(1));
        when(appMessageService.countUnread(USER_ID)).thenReturn(0L);
        stubUpdateChain();
        AppProfileReq req = new AppProfileReq();
        req.setNickname("找娃的老王");
        req.setGender("MALE");
        appUserService.updateProfile(USER_ID, req);
        verify(updateChainWrapper, atLeastOnce()).set(any(), eq("找娃的老王"));
        verify(updateChainWrapper, atLeastOnce()).set(any(), eq(GenderEnum.MALE));
        verify(updateChainWrapper).update();
    }

    @Test
    @DisplayName("资料更新：已注销账号被拒绝")
    void updateProfileShouldRejectCancelledAccount() {
        when(appUserMapper.selectById(USER_ID)).thenReturn(buildUser(4));
        AppProfileReq req = new AppProfileReq();
        req.setNickname("新昵称");
        assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> appUserService.updateProfile(USER_ID, req))
            .withMessage("账号已注销");
        verify(appUserMapper, never()).lambdaUpdate();
    }

    @Test
    @DisplayName("设备登记：未上报设备标识时跳过（不阻断登录）")
    void upsertDeviceShouldSkipWhenDeviceIdMissing() {
        AppLoginReq loginReq = new AppLoginReq();
        appUserService.upsertDevice(USER_ID, loginReq, LocalDateTime.now());
        verify(appDeviceMapper, never()).insert(any(AppDeviceDO.class));
    }

    @Test
    @DisplayName("上下文构建：身份类型与常驻市编码可用于下游判定")
    void buildContextShouldCarryIdentityAndCity() {
        AppUserDO user = buildUser(1);
        user.setIdentityType("VOLUNTEER");
        user.setCityCode("130400");
        AppUserContext context = appUserService.buildContext(user);
        assertThat(context.getId()).isEqualTo(USER_ID);
        assertThat(context.getIdentityType()).isEqualTo("VOLUNTEER");
        assertThat(context.getCityCode()).isEqualTo("130400");
    }

    private void stubUpdateChain() {
        when(appUserMapper.lambdaUpdate()).thenReturn(updateChainWrapper);
    }

    private AppUserDO buildUser(Integer accountStatus) {
        AppUserDO user = new AppUserDO();
        user.setId(USER_ID);
        user.setNickname("无脚星友4821");
        user.setPhone(PHONE);
        user.setAccountStatus(accountStatus);
        user.setIdentityType("PUBLIC");
        user.setRealNameStatus("UNCHECKED");
        return user;
    }

    private AppDeactivateReq buildDeactivateReq() {
        AppDeactivateReq req = new AppDeactivateReq();
        req.setReason("不再使用");
        return req;
    }
}
