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

import cn.hutool.core.util.StrUtil;
import com.guardfind.server.collect.constant.CollectConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 域名级串行限速器
 *
 * <p>
 * 同一域名（含端口，不含查询串）内的请求串行放行，相邻两次请求间隔不低于
 * {@code max(配置间隔, 站点 robots 声明的 Crawl-delay)}；同时记录实测最小间隔，作为"限速是否真的生效"的
 * 可观测证据写入批次日志。采集不做并发抓取，宁慢不扰。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Component
public class DomainRateLimiter {

    /**
     * 首次请求时的实测间隔标记
     */
    private static final long NOT_OBSERVED = -1L;

    /**
     * 域名状态（最后请求时间与实测最小间隔）
     */
    private final Map<String, DomainState> states = new ConcurrentHashMap<>();

    /**
     * 计算实际生效的同域间隔：配置值与站点声明值取较大者
     *
     * @param configuredMs       源配置间隔（毫秒，可为空）
     * @param robotsCrawlDelayMs robots 声明的 Crawl-delay（毫秒，0 表示未声明）
     * @return 生效间隔（毫秒）
     */
    public long requiredIntervalMs(Integer configuredMs, long robotsCrawlDelayMs) {
        long configured = configuredMs == null || configuredMs < 0
            ? CollectConstants.DEFAULT_CRAWL_DELAY_MILLIS
            : configuredMs;
        return Math.max(configured, Math.max(robotsCrawlDelayMs, 0));
    }

    /**
     * 取得一次请求许可（必要时阻塞到间隔满足）
     *
     * @param url           目标地址
     * @param minIntervalMs 最小间隔（毫秒）
     * @return 本次等待时长（毫秒），未等待时为 0
     */
    public long acquire(String url, long minIntervalMs) {
        DomainState state = states.computeIfAbsent(domainOf(url), key -> new DomainState(key));
        synchronized (state) {
            long now = System.currentTimeMillis();
            long last = state.lastRequestAt;
            long waited = 0;
            if (last > 0) {
                long gap = now - last;
                if (gap < minIntervalMs) {
                    waited = minIntervalMs - gap;
                    this.sleep(waited);
                    now = System.currentTimeMillis();
                    gap = now - last;
                }
                state.minObservedGap = state.minObservedGap == NOT_OBSERVED ? gap : Math.min(state.minObservedGap, gap);
            }
            state.lastRequestAt = now;
            if (log.isDebugEnabled() && state.minObservedGap != NOT_OBSERVED) {
                log.debug("同域限速 [{}] 要求间隔 {}ms，实测最小间隔 {}ms", state.domain, minIntervalMs, state.minObservedGap);
            }
            return waited;
        }
    }

    /**
     * 实测最小同域间隔（毫秒）
     *
     * @param url 目标地址（按域名归组）
     * @return 实测间隔；该域名尚未发生第二次请求时为 null
     */
    public Long minObservedGap(String url) {
        DomainState state = states.get(domainOf(url));
        if (state == null || state.minObservedGap == NOT_OBSERVED) {
            return null;
        }
        return state.minObservedGap;
    }

    /**
     * 清空统计（单测与源配置变更后使用）
     */
    public void reset() {
        states.clear();
    }

    /**
     * 解析域名（限速归组键）
     *
     * @param url 地址
     * @return 小写域名（含端口），无法判定时返回 {@code unknown}
     */
    public static String domainOf(String url) {
        if (StrUtil.isBlank(url)) {
            return "unknown";
        }
        String value = StrUtil.subAfter(url.trim(), "://", false);
        if (StrUtil.isBlank(value)) {
            value = url.trim();
        }
        value = StrUtil.subBefore(value, "/", false);
        value = StrUtil.subBefore(value, "?", false);
        return StrUtil.blankToDefault(value, "unknown").toLowerCase();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("同域限速等待被中断，按已等待处理");
        }
    }

    /**
     * 域名状态
     */
    private static class DomainState {

        private final String domain;
        private long lastRequestAt;
        private long minObservedGap = NOT_OBSERVED;

        private DomainState(String domain) {
            this.domain = domain;
        }
    }
}
