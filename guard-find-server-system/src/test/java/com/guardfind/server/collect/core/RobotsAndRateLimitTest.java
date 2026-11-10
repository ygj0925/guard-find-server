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

import com.guardfind.server.collect.model.RobotsDecision;
import com.guardfind.server.collect.model.SourceConfig;
import com.guardfind.server.support.ContainerlessTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * robots 门禁与同域限速单元测试（HTTP 以替身返回，缓存走空实现，不触网不连 Redis）
 */
class RobotsAndRateLimitTest extends ContainerlessTestBase {

    private final HttpFetcher httpFetcher = mock(HttpFetcher.class);

    private final RobotsPolicy robotsPolicy = new RobotsPolicy(httpFetcher) {
        @Override
        protected String readCache(String robotsUrl) {
            return null;
        }

        @Override
        protected void writeCache(String robotsUrl, String content) {
        }
    };

    private SourceConfig config() {
        SourceConfig config = new SourceConfig();
        config.setCode("remote");
        config.setBaseUrl("https://example.org");
        config.setCategoryIds(List.of("1"));
        config.setListPathTpl("/xunren/category/{categoryId}?p-page={page}");
        config.setDetailPathTpl("/xunren/view/{sourceId}");
        config.setCrawlDelayMs(0);
        config.setMaxPages(1);
        config.setRequestTimeoutMs(1000);
        return config;
    }

    private void stubRobots(HttpFetcher.HttpResult result) {
        when(httpFetcher.get(anyString(), anyInt(), anyLong())).thenReturn(result);
    }

    @Test
    @DisplayName("robots 全站 Disallow → 中止")
    void disallowAllAborts() {
        stubRobots(HttpFetcher.HttpResult
            .success("https://example.org/robots.txt", 200, "User-agent: *\nDisallow: /\n", 1));
        RobotsDecision decision = robotsPolicy.check(config());
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.allows("https://example.org/xunren/view/1")).isFalse();
    }

    @Test
    @DisplayName("Disallow 按路径前缀生效，更长的 Allow 优先，$ 锚定末尾")
    void prefixRulesFollowRobotsConvention() {
        RobotsDecision decision = RobotsDecision.allow("https://example.org/robots.txt");
        decision.getDisallows().add("/xunren");
        decision.getAllows().add("/xunren/view/");
        decision.getDisallows().add("/*.pdf$");
        assertThat(decision.allows("https://example.org/xunren/category/1?p-page=2")).isFalse();
        assertThat(decision.allows("https://example.org/xunren/view/1")).isTrue();
        assertThat(decision.allows("https://example.org/files/a.pdf")).isFalse();
        assertThat(decision.allows("https://example.org/files/a.pdf.html")).isTrue();
        assertThat(decision.allows("https://example.org/about")).isTrue();
    }

    @Test
    @DisplayName("robots 禁止的是采集路径 → 中止")
    void disallowCollectPathAborts() {
        stubRobots(HttpFetcher.HttpResult
            .success("https://example.org/robots.txt", 200, "User-agent: *\nDisallow: /xunren/\n", 1));
        assertThat(robotsPolicy.check(config()).isAllowed()).isFalse();
    }

    @Test
    @DisplayName("robots 拿不到 → 中止，不放行")
    void unavailableAborts() {
        stubRobots(HttpFetcher.HttpResult.failure("https://example.org/robots.txt", 503, "unavailable", 3, true));
        assertThat(robotsPolicy.check(config()).isAllowed()).isFalse();
    }

    @Test
    @DisplayName("robots 允许且声明 Crawl-delay → 放行并带出延迟")
    void allowCarriesCrawlDelay() {
        stubRobots(HttpFetcher.HttpResult
            .success("https://example.org/robots.txt", 200, "User-agent: *\nAllow: /\nCrawl-delay: 3\n", 1));
        RobotsDecision decision = robotsPolicy.check(config());
        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.getCrawlDelayMillis()).isEqualTo(3000L);
    }

    @Test
    @DisplayName("生效间隔取配置值与站点声明值的较大者")
    void requiredIntervalTakesTheStricter() {
        DomainRateLimiter limiter = new DomainRateLimiter();
        assertThat(limiter.requiredIntervalMs(2000, 0)).isEqualTo(2000L);
        assertThat(limiter.requiredIntervalMs(2000, 5000)).isEqualTo(5000L);
        assertThat(limiter.requiredIntervalMs(null, 0)).isPositive();
    }

    @Test
    @DisplayName("同域连续请求的实测间隔不低于配置值")
    void observedGapRespectsInterval() {
        DomainRateLimiter limiter = new DomainRateLimiter();
        long interval = 120;
        limiter.acquire("https://example.org/a", interval);
        limiter.acquire("https://example.org/b", interval);
        limiter.acquire("https://example.org/c", interval);
        assertThat(limiter.minObservedGap("https://example.org/robots.txt")).isGreaterThanOrEqualTo(interval);
    }
}
