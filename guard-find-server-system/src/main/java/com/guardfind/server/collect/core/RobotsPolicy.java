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

package com.guardfind.server.collect.core;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.guardfind.server.collect.constant.CollectConstants;
import com.guardfind.server.collect.model.RobotsDecision;
import com.guardfind.server.collect.model.SourceConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.continew.starter.cache.redisson.util.RedisUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * robots 合规判定
 *
 * <p>
 * 每轮采集开始前读取目标站点 {@code {base}/robots.txt}（Hutool HTTP，UA 与详情抓取一致），原文按 24 小时缓存，
 * 避免反复打扰站方。解析 {@code User-agent: *} 组的 Disallow / Allow / Crawl-delay。
 * </p>
 *
 * <p>
 * <b>本类不提供"忽略 robots"的开关，也不提供绕过入口</b>：拿不到、解析不了、命中禁止，一律返回中止结果由编排器
 * 记录原因后结束本轮。缺省语义为白名单——判不出来就是不能采。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RobotsPolicy {

    /**
     * 通用抓取标识（本项目 UA 的令牌名，与 {@link HttpFetcher#USER_AGENT} 对应）
     */
    private static final String WILDCARD_AGENT = "*";

    private final HttpFetcher httpFetcher;

    /**
     * 本地兜底缓存（Redis 不可用时使用，仅影响性能，不影响判定）
     */
    private final Map<String, CachedRobots> localCache = new ConcurrentHashMap<>();

    /**
     * 判定源站点本轮是否可采
     *
     * @param config 源运行配置
     * @return 判定结果（允许 / 中止 + 原因 + 站点声明的 Crawl-delay）
     */
    public RobotsDecision check(SourceConfig config) {
        String robotsUrl = robotsUrlOf(config.getBaseUrl());
        if (StrUtil.isBlank(robotsUrl)) {
            return RobotsDecision.abort(robotsUrl, CollectConstants.ABORT_ROBOTS_UNAVAILABLE, "源站点根地址为空，无法判定 robots");
        }
        String cached = this.readCache(robotsUrl);
        RobotsDecision decision;
        if (cached != null) {
            decision = this.parse(robotsUrl, cached, config);
            decision.setFromCache(true);
            return decision;
        }
        HttpFetcher.HttpResult result = httpFetcher.get(robotsUrl, config.getRequestTimeoutMs(), config
            .getCrawlDelayMs());
        if (!result.isSuccess()) {
            String reason = "robots.txt 无法获取（HTTP %d，%s）：本轮中止，不发起任何抓取".formatted(result.getStatus(), StrUtil
                .blankToDefault(result.getErrorMessage(), "无响应"));
            log.warn("采集中止：{} -> {}", robotsUrl, reason);
            return RobotsDecision.abort(robotsUrl, CollectConstants.ABORT_ROBOTS_UNAVAILABLE, reason);
        }
        if (StrUtil.isBlank(result.getBody())) {
            return RobotsDecision
                .abort(robotsUrl, CollectConstants.ABORT_ROBOTS_UNPARSABLE, "robots.txt 内容为空，无法判定授权范围：本轮中止");
        }
        decision = this.parse(robotsUrl, result.getBody(), config);
        if (decision.isAllowed()) {
            this.writeCache(robotsUrl, result.getBody());
        }
        return decision;
    }

    /**
     * 拼装 robots.txt 地址
     *
     * @param baseUrl 站点根地址
     * @return robots 地址
     */
    public static String robotsUrlOf(String baseUrl) {
        if (StrUtil.isBlank(baseUrl)) {
            return null;
        }
        String base = baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/robots.txt";
    }

    /**
     * 解析 robots 文本（解析异常按"无法判定"中止）
     */
    private RobotsDecision parse(String robotsUrl, String content, SourceConfig config) {
        try {
            RobotsDecision decision = RobotsDecision.allow(robotsUrl);
            boolean matchedGroup = false;
            boolean inTargetGroup = false;
            for (String rawLine : StrUtil.split(StrUtil.trim(content), '\n')) {
                String line = StrUtil.subBefore(rawLine, "#", false).trim();
                if (StrUtil.isEmpty(line)) {
                    continue;
                }
                int split = line.indexOf(':');
                if (split < 0) {
                    continue;
                }
                String key = line.substring(0, split).trim().toLowerCase();
                String value = line.substring(split + 1).trim();
                if ("user-agent".equals(key)) {
                    inTargetGroup = WILDCARD_AGENT.equals(value);
                    matchedGroup = matchedGroup || inTargetGroup;
                    continue;
                }
                if (!inTargetGroup) {
                    continue;
                }
                switch (key) {
                    case "disallow" -> {
                        // "Disallow:" 空值表示允许抓取该 UA 的全部内容
                        if (StrUtil.isNotBlank(value)) {
                            decision.getDisallows().add(value);
                        }
                    }
                    case "allow" -> {
                        if (StrUtil.isNotBlank(value)) {
                            decision.getAllows().add(value);
                        }
                    }
                    case "crawl-delay" -> decision.setCrawlDelayMillis(this.parseCrawlDelay(value));
                    default -> {
                        // 其他指令（sitemap/disallow 之外的扩展）与判定无关，忽略
                    }
                }
            }
            if (!matchedGroup) {
                // 未对通用 UA 声明规则：视为无限制（这是"读到了且读懂了"，不属于解析失败）
                log.info("robots.txt 未对 User-agent: {} 声明规则：{}", WILDCARD_AGENT, robotsUrl);
                return decision;
            }
            // 预检本轮将要访问的列表与详情路径，命中禁止即整轮中止，不留下"部分抓取"的中间态
            String blocked = firstBlockedPath(decision, config);
            if (blocked != null) {
                return RobotsDecision
                    .abort(robotsUrl, CollectConstants.ABORT_ROBOTS_DISALLOWED, "robots.txt 对 User-agent: %s 禁止抓取路径 %s：本轮中止，不发起任何抓取"
                        .formatted(WILDCARD_AGENT, RobotsDecision.pathOf(blocked)));
            }
            return decision;
        } catch (Exception e) {
            log.warn("robots.txt 解析失败：{}，原因：{}", robotsUrl, e.getMessage());
            return RobotsDecision
                .abort(robotsUrl, CollectConstants.ABORT_ROBOTS_UNPARSABLE, "robots.txt 解析失败，无法判定授权范围：本轮中止（%s）"
                    .formatted(e.getMessage()));
        }
    }

    /**
     * 取本轮预检地址（列表首页 + 详情模板），用于在建连之前就拦下被禁止的路径
     */
    private String firstBlockedPath(RobotsDecision decision, SourceConfig config) {
        List<String> probes = new ArrayList<>();
        String categoryId = CollUtil.isEmpty(config.getCategoryIds()) ? null : config.getCategoryIds().get(0);
        probes.add(config.buildListUrl(categoryId, 1));
        probes.add(config.buildDetailUrl("0"));
        for (String probe : probes) {
            if (StrUtil.isNotBlank(probe) && !decision.allows(probe)) {
                return probe;
            }
        }
        return null;
    }

    /**
     * 判断单个地址是否被允许（供编排器逐条兜底，未知一律拒绝）
     *
     * @param decision 判定结果
     * @param url      目标地址
     * @return true：允许
     */
    public boolean isAllowed(RobotsDecision decision, String url) {
        return decision != null && decision.allows(url);
    }

    /**
     * 站点声明的抓取间隔（毫秒）
     *
     * @param decision 判定结果
     * @return 间隔，未声明时为 0
     */
    public long crawlDelayMillis(RobotsDecision decision) {
        return decision == null ? 0 : decision.getCrawlDelayMillis();
    }

    private long parseCrawlDelay(String value) {
        if (StrUtil.isBlank(value) || !NumberUtil.isNumber(StrUtil.subBefore(value, " ", false))) {
            return 0;
        }
        double seconds = Double.parseDouble(StrUtil.subBefore(value, " ", false));
        return Math.round(seconds * 1000);
    }

    /**
     * 读取缓存（Redis 优先，异常时降级到本地缓存，不影响判定结果）
     */
    protected String readCache(String robotsUrl) {
        String key = CollectConstants.ROBOTS_CACHE_KEY_PREFIX + DomainRateLimiter.domainOf(robotsUrl);
        try {
            String cached = RedisUtils.get(key);
            if (StrUtil.isNotBlank(cached)) {
                return cached;
            }
        } catch (Exception e) {
            log.warn("robots 缓存读取失败，降级为本地缓存：{}", e.getMessage());
        }
        CachedRobots local = localCache.get(key);
        if (local != null && local.expireAt() > System.currentTimeMillis()) {
            return local.content();
        }
        return null;
    }

    /**
     * 写入缓存（24 小时）
     */
    protected void writeCache(String robotsUrl, String content) {
        String key = CollectConstants.ROBOTS_CACHE_KEY_PREFIX + DomainRateLimiter.domainOf(robotsUrl);
        localCache.put(key, new CachedRobots(content, System.currentTimeMillis() + CollectConstants.ROBOTS_CACHE_TTL
            .toMillis()));
        try {
            RedisUtils.set(key, content, CollectConstants.ROBOTS_CACHE_TTL);
        } catch (Exception e) {
            log.warn("robots 缓存写入失败，仅使用本地缓存：{}", e.getMessage());
        }
    }

    /**
     * 清理缓存（源侧规则变更或单测使用）
     *
     * @param baseUrl 站点根地址
     */
    public void evict(String baseUrl) {
        String key = CollectConstants.ROBOTS_CACHE_KEY_PREFIX + DomainRateLimiter.domainOf(robotsUrlOf(baseUrl));
        localCache.remove(key);
        try {
            RedisUtils.delete(key);
        } catch (Exception e) {
            log.warn("robots 缓存清理失败：{}", e.getMessage());
        }
    }

    /**
     * 本地缓存项
     *
     * @param content  robots 原文
     * @param expireAt 过期时间戳
     */
    private record CachedRobots(String content, long expireAt) {
    }
}
