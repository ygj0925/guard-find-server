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

package com.guardfind.server.app.oauth;

import com.guardfind.server.app.model.entity.AppUserDO;

import com.guardfind.server.app.mapper.AppUserBindingMapper;
import com.guardfind.server.app.model.entity.AppUserBindingDO;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.starter.core.exception.BusinessException;

/**
 * 三方绑定 Service:绑定/解绑/凭 openid 查找/自动创建用户。
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Service
@RequiredArgsConstructor
public class OAuthBindingService {

    private final AppUserBindingMapper bindingMapper;
    private final AppUserMapper appUserMapper;

    /** 通过 openid 查找已绑定的用户;未绑定返回 null */
    public AppUserDO findByOpenid(String provider, String openid) {
        AppUserBindingDO binding = bindingMapper.selectOne(Wrappers.<AppUserBindingDO>lambdaQuery()
            .eq(AppUserBindingDO::getProvider, provider)
            .eq(AppUserBindingDO::getOpenid, openid)
            .last("LIMIT 1"));
        return binding == null ? null : appUserMapper.selectById(binding.getUserId());
    }

    /** 绑定三方账号到已有用户 */
    @Transactional(rollbackFor = Exception.class)
    public void bind(Long userId, String provider, OAuthUserInfo info) {
        Long exists = bindingMapper.selectCount(Wrappers.<AppUserBindingDO>lambdaQuery()
            .eq(AppUserBindingDO::getProvider, provider)
            .eq(AppUserBindingDO::getOpenid, info.getOpenid()));
        if (exists > 0) {
            throw new BusinessException("该三方账号已被绑定");
        }
        AppUserBindingDO binding = new AppUserBindingDO();
        binding.setUserId(userId);
        binding.setProvider(provider);
        binding.setOpenid(info.getOpenid());
        binding.setUnionid(info.getUnionid());
        binding.setNickname(info.getNickname());
        binding.setAvatar(info.getAvatar());
        bindingMapper.insert(binding);
    }

    /** 自动创建新用户(三方首次登录且未注册) */
    @Transactional(rollbackFor = Exception.class)
    public AppUserDO createFromOAuth(String provider, OAuthUserInfo info) {
        AppUserDO user = new AppUserDO();
        user.setNickname(info.getNickname() != null ? info.getNickname() : "用户" + info.getOpenid().substring(0, 6));
        user.setAvatar(info.getAvatar());
        appUserMapper.insert(user);
        bind(user.getId(), provider, info);
        return user;
    }

    /** 解绑 */
    public void unbind(Long userId, String provider) {
        bindingMapper.delete(Wrappers.<AppUserBindingDO>lambdaQuery()
            .eq(AppUserBindingDO::getUserId, userId)
            .eq(AppUserBindingDO::getProvider, provider));
    }
}
