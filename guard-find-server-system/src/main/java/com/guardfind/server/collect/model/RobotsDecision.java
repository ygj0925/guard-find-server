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

package com.guardfind.server.collect.model;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.guardfind.server.collect.constant.CollectConstants;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * robots 判定结果
 *
 * <p>
 * 三态而非布尔：<b>允许</b>、<b>明确禁止</b>、<b>无法判定</b>。后两者一律中止本轮采集——"拿不到规则"不等于
 * "没有规则"，静默放行才是对站方的冒犯。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
public class RobotsDecision implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * robots.txt 地址
     */
    private String robotsUrl;

    /**
     * 是否允许继续抓取
     */
    private boolean allowed;

    /**
     * 中止原因码（ROBOTS_DISALLOWED / ROBOTS_UNAVAILABLE / ROBOTS_UNPARSABLE）
     */
    private String abortCode;

    /**
     * 面向运维的中文说明
     */
    private String reason;

    /**
     * 站点声明的 Crawl-delay（毫秒，0 表示未声明）
     */
    private long crawlDelayMillis;

    /**
     * 对 User-agent: * 生效的 Disallow 前缀
     */
    private final List<String> disallows = new ArrayList<>();

    /**
     * 对 User-agent: * 生效的 Allow 前缀
     */
    private final List<String> allows = new ArrayList<>();

    /**
     * 结果是否来自缓存
     */
    private boolean fromCache;

    /**
     * 构造"允许"结果
     *
     * @param robotsUrl robots 地址
     * @return 判定结果
     */
    public static RobotsDecision allow(String robotsUrl) {
        RobotsDecision decision = new RobotsDecision();
        decision.robotsUrl = robotsUrl;
        decision.allowed = true;
        decision.reason = "robots.txt 允许采集";
        return decision;
    }

    /**
     * 构造"中止"结果
     *
     * @param robotsUrl robots 地址
     * @param abortCode 中止原因码
     * @param reason    中文说明
     * @return 判定结果
     */
    public static RobotsDecision abort(String robotsUrl, String abortCode, String reason) {
        RobotsDecision decision = new RobotsDecision();
        decision.robotsUrl = robotsUrl;
        decision.allowed = false;
        decision.abortCode = StrUtil.blankToDefault(abortCode, CollectConstants.ABORT_ROBOTS_UNAVAILABLE);
        decision.reason = reason;
        return decision;
    }

    /**
     * 判断某个具体地址是否被允许抓取（未判定时一律拒绝，白名单语义）
     *
     * @param url 目标地址
     * @return true：允许
     */
    public boolean allows(String url) {
        if (!allowed) {
            return false;
        }
        String path = pathOf(url);
        if (CollUtil.isEmpty(disallows)) {
            return true;
        }
        String disallow = longestMatch(disallows, path);
        String allow = longestMatch(allows, path);
        if (disallow == null) {
            return true;
        }
        // Allow 更具体时以 Allow 为准（robots 惯例）
        return allow != null && allow.length() > disallow.length();
    }

    /**
     * 提取地址中用于比对 robots 的路径部分
     *
     * @param url 目标地址
     * @return 路径（以 / 开头）
     */
    public static String pathOf(String url) {
        String value = StrUtil.subAfter(StrUtil.emptyIfNull(url).trim(), "://", false);
        if (StrUtil.isBlank(value)) {
            value = StrUtil.emptyIfNull(url).trim();
        }
        String path = StrUtil.subAfter(value, "/", false);
        if (StrUtil.isEmpty(path) || path.equals(value)) {
            return "/";
        }
        return "/" + StrUtil.subBefore(path, "?", false);
    }

    private static String longestMatch(List<String> prefixes, String path) {
        String best = null;
        for (String prefix : prefixes) {
            if (StrUtil.isEmpty(prefix) || "/".equals(prefix)) {
                continue;
            }
            if (matches(path, prefix) && (best == null || prefix.length() > best.length())) {
                best = prefix;
            }
        }
        return best;
    }

    /**
     * robots 通配规则：{@code *} 匹配任意串，结尾 {@code $} 表示精确匹配
     */
    private static boolean matches(String path, String rule) {
        String regex = ("\\Q" + rule.replace("$", "") + "\\E").replace("*", "\\E.*\\Q");
        String target = rule.endsWith("$") ? path : StrUtil.subBefore(path, "?", false);
        return rule.endsWith("$") ? target.matches(regex + "$") : target.matches(regex);
    }
}
