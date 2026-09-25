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
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.guardfind.server.collect.constant.CollectConstants;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.Serial;
import java.io.Serializable;

/**
 * 采集 HTTP 抓取器
 *
 * <p>
 * 基于 Hutool {@code HttpRequest} 的统一出口：明示 {@link CollectConstants#USER_AGENT}、受超时约束、
 * 5xx 与网络异常按指数退避最多重试 {@value CollectConstants#MAX_RETRY_TIMES} 次，4xx 属站方明确拒绝，
 * <b>不重试</b>（重试只会加重对方负载）。所有请求先过域名限速器。失败不抛异常，而是返回带原因的结果，
 * 由编排器记入失败明细——单个页面失败不许拖垮整批。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpFetcher {

    /**
     * 采集器 User-Agent（对外可联系到采集方）
     */
    public static final String USER_AGENT = CollectConstants.USER_AGENT;

    private final DomainRateLimiter domainRateLimiter;

    /**
     * GET 请求（合规默认值：默认超时与默认同域间隔）
     *
     * @param url 地址
     * @return 抓取结果
     */
    public HttpResult get(String url) {
        return this.get(url, CollectConstants.DEFAULT_TIMEOUT_MILLIS, CollectConstants.DEFAULT_CRAWL_DELAY_MILLIS);
    }

    /**
     * GET 请求
     *
     * @param url           地址
     * @param timeoutMs     超时（毫秒）
     * @param minIntervalMs 同域最小间隔（毫秒）
     * @return 抓取结果（含成功标记、状态码、正文与失败原因）
     */
    public HttpResult get(String url, int timeoutMs, long minIntervalMs) {
        int maxAttempts = CollectConstants.MAX_RETRY_TIMES + 1;
        HttpResult last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1) {
                this.backoff(attempt);
            }
            domainRateLimiter.acquire(url, minIntervalMs);
            last = this.doGet(url, timeoutMs, attempt);
            if (last.isSuccess() || !last.isRetryable()) {
                return last;
            }
            log.warn("采集请求失败（第 {} 次尝试）：url={}，原因={}", attempt, url, last.getErrorMessage());
        }
        return last;
    }

    /**
     * 单次请求（不重试、不限速），供 {@link #get(String, int, long)} 循环调用
     */
    private HttpResult doGet(String url, int timeoutMs, int attempt) {
        try (HttpResponse response = HttpRequest.get(url)
            .header(Header.USER_AGENT, USER_AGENT)
            .timeout(timeoutMs)
            .execute()) {
            int status = response.getStatus();
            if (response.isOk()) {
                return HttpResult.success(url, status, response.body(), attempt);
            }
            // 4xx 为站方明确响应，不重试；其余按可重试失败处理
            boolean clientError = status >= 400 && status < 500;
            return HttpResult.failure(url, status, "HTTP 状态码 " + status, attempt, !clientError);
        } catch (Exception e) {
            return HttpResult.failure(url, 0, StrUtil.blankToDefault(e.getMessage(), e.getClass()
                .getSimpleName()), attempt, true);
        }
    }

    /**
     * 指数退避：第 n 次尝试前等待 {@code 基数 << (n-2)}
     */
    private void backoff(int attempt) {
        long millis = CollectConstants.RETRY_BACKOFF_MILLIS << (attempt - 2);
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 抓取结果
     */
    @Data
    public static class HttpResult implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 是否成功（HTTP 2xx）
         */
        private boolean success;

        /**
         * HTTP 状态码（0 表示未获得响应）
         */
        private int status;

        /**
         * 响应正文
         */
        private String body;

        /**
         * 失败原因
         */
        private String errorMessage;

        /**
         * 已尝试次数
         */
        private int attempts;

        /**
         * 是否可重试
         */
        private boolean retryable;

        /**
         * 请求地址
         */
        private String url;

        static HttpResult success(String url, int status, String body, int attempts) {
            HttpResult result = new HttpResult();
            result.setUrl(url);
            result.setSuccess(true);
            result.setStatus(status);
            result.setBody(body);
            result.setAttempts(attempts);
            return result;
        }

        static HttpResult failure(String url, int status, String errorMessage, int attempts, boolean retryable) {
            HttpResult result = new HttpResult();
            result.setUrl(url);
            result.setSuccess(false);
            result.setStatus(status);
            result.setErrorMessage(errorMessage);
            result.setAttempts(attempts);
            result.setRetryable(retryable);
            return result;
        }
    }
}
