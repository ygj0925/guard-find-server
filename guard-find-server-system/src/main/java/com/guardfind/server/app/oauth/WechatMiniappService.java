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

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.guardfind.server.app.oauth.OAuthProperties.ProviderConfig;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.continew.starter.core.exception.BusinessException;

/**
 * 微信小程序登录服务:wx.login 的 code 换 openid(jscode2session)。
 * AppID/AppSecret 复用 guardfind.oauth.wechat 配置,留空 = 未接入(登录直接拒绝)。
 *
 * @author guardfind
 * @since 2026/11/3
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatMiniappService {

    private static final String JSCODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code";

    private final OAuthProperties oauthProperties;

    /**
     * code 换 openid;未配置或微信侧报错均抛业务异常
     */
    public WxSession jsCode2Session(String code) {
        ProviderConfig config = oauthProperties.getWechat();
        if (StrUtil.isBlank(config.getAppId()) || StrUtil.isBlank(config.getAppSecret())) {
            throw new BusinessException("微信登录暂未开放,请使用其他方式登录");
        }
        String url = String.format(JSCODE2SESSION_URL, config.getAppId(), config.getAppSecret(), StrUtil.trim(code));
        String body;
        try {
            body = HttpUtil.get(url, 5000);
        } catch (Exception e) {
            log.warn("[wechat] jscode2session 请求失败: {}", e.getMessage());
            throw new BusinessException("微信登录失败,请稍后再试");
        }
        JSONObject json;
        try {
            json = JSONUtil.parseObj(body);
        } catch (Exception e) {
            log.warn("[wechat] jscode2session 响应解析失败: {}", StrUtil.maxLength(body, 200));
            throw new BusinessException("微信登录失败,请稍后再试");
        }
        Integer errcode = json.getInt("errcode", 0);
        if (errcode != 0) {
            log.warn("[wechat] jscode2session 返回错误 errcode={} errmsg={}", errcode, json.getStr("errmsg"));
            throw new BusinessException("微信登录失败,请稍后再试");
        }
        WxSession session = new WxSession();
        session.setOpenid(json.getStr("openid"));
        session.setUnionid(json.getStr("unionid"));
        session.setSessionKey(json.getStr("session_key"));
        if (StrUtil.isBlank(session.getOpenid())) {
            throw new BusinessException("微信登录失败,请稍后再试");
        }
        return session;
    }

    /**
     * jscode2session 响应(openid 必有;unionid 需绑定开放平台才有)
     */
    @Data
    public static class WxSession {

        private String openid;
        private String unionid;
        private String sessionKey;
    }
}
