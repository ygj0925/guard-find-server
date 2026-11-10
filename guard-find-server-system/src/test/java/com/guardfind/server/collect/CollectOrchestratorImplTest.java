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

import com.guardfind.server.collect.constant.CollectConstants;
import com.guardfind.server.collect.core.CandidateMapper;
import com.guardfind.server.collect.core.CollectStore;
import com.guardfind.server.collect.core.DomainRateLimiter;
import com.guardfind.server.collect.core.RobotsPolicy;
import com.guardfind.server.collect.model.RobotsDecision;
import com.guardfind.server.collect.model.SourceConfig;
import com.guardfind.server.collect.model.entity.CollectCandidateDO;
import com.guardfind.server.collect.model.entity.CollectLogDO;
import com.guardfind.server.collect.model.entity.CollectSourceDO;
import com.guardfind.server.collect.model.resp.CollectRunResp;
import com.guardfind.server.collect.service.impl.CollectOrchestratorImpl;
import com.guardfind.server.collect.source.CollectSource;
import com.guardfind.server.collect.source.LocalFixtureSource;
import com.guardfind.server.collect.source.XunrenHtmlParser;
import com.guardfind.server.support.ContainerlessTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 采集编排单元测试：robots 中止、重跑幂等、原文留存期清理（不触网、不连库）
 */
class CollectOrchestratorImplTest extends ContainerlessTestBase {

    private final CollectStore store = mock(CollectStore.class);
    private final RobotsPolicy robotsPolicy = mock(RobotsPolicy.class);
    private final DomainRateLimiter rateLimiter = mock(DomainRateLimiter.class);

    private CollectOrchestratorImpl orchestrator(CollectSource source) {
        return new CollectOrchestratorImpl(store, robotsPolicy, rateLimiter, new CandidateMapper(), List.of(source));
    }

    private CollectSourceDO sourceDO(String code, String baseUrl) {
        CollectSourceDO sourceDO = new CollectSourceDO();
        sourceDO.setId(9L);
        sourceDO.setCode(code);
        sourceDO.setName(code);
        sourceDO.setBaseUrl(baseUrl);
        sourceDO.setCategoryIds(List.of("default"));
        sourceDO.setCrawlDelayMs(0);
        sourceDO.setMaxPages(1);
        sourceDO.setRequestTimeoutMs(1000);
        return sourceDO;
    }

    @Test
    @DisplayName("robots 禁止即中止：不翻页、不抓详情，但仍落批次日志")
    void robotsDisallowAbortsWholeRun() {
        CollectSource source = mock(CollectSource.class);
        when(source.code()).thenReturn("remote");
        when(source.robotsRequired()).thenReturn(true);
        when(store.findSource("remote")).thenReturn(Optional.of(sourceDO("remote", "https://example.org")));
        when(robotsPolicy.check(any(SourceConfig.class))).thenReturn(RobotsDecision
            .abort("https://example.org/robots.txt", CollectConstants.ABORT_ROBOTS_UNAVAILABLE, "robots 禁止"));

        CollectRunResp result = orchestrator(source).run("remote", null, CollectConstants.TRIGGER_MANUAL);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getSkippedReason()).isEqualTo("robots 禁止");
        verify(source, never()).fetchListPageUrls(any(SourceConfig.class), anyInt());
        verify(source, never()).fetchDetail(any(SourceConfig.class), anyString());
        verify(store, never()).insertCandidate(any());
        ArgumentCaptor<CollectLogDO> logCaptor = ArgumentCaptor.forClass(CollectLogDO.class);
        verify(store).insertLog(logCaptor.capture());
        assertThat(logCaptor.getValue().getSuccess()).isFalse();
    }

    @Test
    @DisplayName("重跑同一页集合：已有同来源候选则新增为 0、不再插入候选")
    void rerunIsIdempotent() {
        LocalFixtureSource source = new LocalFixtureSource(new XunrenHtmlParser());
        when(store.findSource(LocalFixtureSource.CODE)).thenReturn(Optional
            .of(sourceDO(LocalFixtureSource.CODE, "local://fixtures")));
        when(store.findCandidate(any(), any())).thenReturn(Optional.of(new CollectCandidateDO()));

        CollectRunResp result = orchestrator(source).run(LocalFixtureSource.CODE, 1, CollectConstants.TRIGGER_MANUAL);

        assertThat(result.getFetched()).isPositive();
        assertThat(result.getAdded()).isZero();
        verify(store, never()).insertCandidate(any());
    }

    @Test
    @DisplayName("原文留存期清理：阈值为当前时间前推 180 天")
    void purgeUsesRetentionThreshold() {
        when(store.clearRawContentFetchedBefore(any())).thenReturn(3);
        LocalDateTime before = LocalDateTime.now().minusDays(CollectConstants.RAW_RETENTION_DAYS);

        int cleared = orchestrator(new LocalFixtureSource(new XunrenHtmlParser())).purgeExpiredRawContent();

        LocalDateTime after = LocalDateTime.now().minusDays(CollectConstants.RAW_RETENTION_DAYS);
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(store).clearRawContentFetchedBefore(captor.capture());
        assertThat(cleared).isEqualTo(3);
        assertThat(captor.getValue()).isBetween(before, after);
    }
}
