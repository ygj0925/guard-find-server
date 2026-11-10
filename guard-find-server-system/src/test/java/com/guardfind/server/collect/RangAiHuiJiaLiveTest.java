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

package com.guardfind.server.collect;

import com.guardfind.server.collect.core.DomainRateLimiter;
import com.guardfind.server.collect.core.HttpFetcher;
import com.guardfind.server.collect.core.RobotsPolicy;
import com.guardfind.server.collect.model.CandidateDraft;
import com.guardfind.server.collect.model.RobotsDecision;
import com.guardfind.server.collect.model.SourceConfig;
import com.guardfind.server.collect.source.RangAiHuiJiaSource;
import com.guardfind.server.collect.source.XunrenHtmlParser;
import com.guardfind.server.support.ContainerlessTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实源联网冒烟（默认不执行）
 *
 * <p>
 * 仅在取得站方书面授权后、以 {@code -Dcollect.live=true} 显式开启；只抓 robots、列表第 1 页与 1 条详情，
 * 同域间隔按 2 秒执行，不落库。
 * </p>
 */
@EnabledIfSystemProperty(named = "collect.live", matches = "true")
class RangAiHuiJiaLiveTest extends ContainerlessTestBase {

    private final DomainRateLimiter rateLimiter = new DomainRateLimiter();
    private final HttpFetcher httpFetcher = new HttpFetcher(rateLimiter);
    private final RobotsPolicy robotsPolicy = new RobotsPolicy(httpFetcher) {
        @Override
        protected String readCache(String robotsUrl) {
            return null;
        }

        @Override
        protected void writeCache(String robotsUrl, String content) {
        }
    };

    @Test
    @DisplayName("robots 放行后抓列表第 1 页与 1 条详情，实测同域间隔不低于配置")
    void smoke() {
        SourceConfig config = new SourceConfig();
        config.setCode(RangAiHuiJiaSource.CODE);
        config.setBaseUrl("https://www.rangaihuijia.com");
        config.setListPathTpl("/xunren/category/{categoryId}?p-page={page}");
        config.setDetailPathTpl("/xunren/view/{sourceId}");
        config.setCategoryIds(List.of("6"));
        config.setCrawlDelayMs(2000);
        config.setMaxPages(1);
        config.setRequestTimeoutMs(10000);

        RobotsDecision decision = robotsPolicy.check(config);
        assertThat(decision.isAllowed()).as(decision.getReason()).isTrue();

        RangAiHuiJiaSource source = new RangAiHuiJiaSource(httpFetcher, new XunrenHtmlParser());
        List<String> urls = source.fetchListPageUrls(config, 1);
        assertThat(urls).isNotEmpty();
        Optional<CandidateDraft> draft = source.fetchDetail(config, urls.get(0));
        assertThat(draft).isPresent();
        assertThat(draft.get().getSourceId()).isNotBlank();
        assertThat(rateLimiter.minObservedGap(RobotsPolicy.robotsUrlOf(config.getBaseUrl())))
            .isGreaterThanOrEqualTo(2000L);
    }
}
