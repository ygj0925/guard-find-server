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

import com.guardfind.server.collect.constant.CollectConstants;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * HTTP 抓取器单元测试：使用 loopback HTTP 服务和真实 socket，不访问真实站点、不加载 Spring 上下文。
 *
 * <p>
 * 仅模拟域名限速器；读超时由 latch 阻塞响应正文触发，重试仍执行生产代码的指数退避。
 * </p>
 */
@Timeout(30)
class HttpFetcherTest {

    private static final int REQUEST_TIMEOUT_MS = 5000;
    private static final long MIN_INTERVAL_MS = 1500L;

    private final DomainRateLimiter rateLimiter = mock(DomainRateLimiter.class);
    private final HttpFetcher httpFetcher = new HttpFetcher(rateLimiter);
    private final AtomicInteger acquiredPermits = new AtomicInteger();
    private final Queue<Integer> permitsSeenByRequests = new ConcurrentLinkedQueue<>();
    private final CountDownLatch releaseResponses = new CountDownLatch(1);

    private HttpServer server;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        when(rateLimiter.acquire(anyString(), anyLong())).thenAnswer(invocation -> {
            acquiredPermits.incrementAndGet();
            return 0L;
        });
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        // 断言失败也必须唤醒所有超时夹具处理器，再关闭服务及其工作线程。
        releaseResponses.countDown();
        try {
            if (server != null) {
                server.stop(0);
            }
        } finally {
            if (executor != null) {
                executor.shutdownNow();
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).as("本地 HTTP 服务线程池应完全退出").isTrue();
            }
        }
    }

    @Test
    @DisplayName("本地响应正文读超时：失败状态为 0，重试至 3 次且每次先经过域名限速器")
    void shouldRetryReadTimeoutUntilAttemptsAreExhausted() throws IOException, InterruptedException {
        int timeoutMs = 500;
        CountDownLatch responsesStarted = new CountDownLatch(3);
        byte[] body = "delayed response".getBytes(StandardCharsets.UTF_8);
        String url = this.startServer(exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().flush();
            responsesStarted.countDown();
            try {
                // 响应头已发出，但正文一直阻塞到测试清理，不能用连接失败替代读超时。
                releaseResponses.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            // 清理时客户端可能已关闭 socket，IOException 仍会经过外层 finally 关闭 exchange。
            exchange.getResponseBody().write(body);
        });

        long startedAt = System.nanoTime();
        HttpFetcher.HttpResult result = httpFetcher.get(url, timeoutMs, MIN_INTERVAL_MS);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isZero();
        assertThat(result.getErrorMessage()).isNotBlank();
        assertThat(result.getAttempts()).isEqualTo(3).isEqualTo(CollectConstants.MAX_RETRY_TIMES + 1);
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getUrl()).isEqualTo(url);
        assertThat(responsesStarted.await(5, TimeUnit.SECONDS)).as("三次尝试均应通过真实本地 socket 收到响应头并等待正文").isTrue();
        this.assertRequestsPassedRateLimiter(url, 1, 2, 3);

        long expectedWaitMs = 3L * timeoutMs + CollectConstants.RETRY_BACKOFF_MILLIS + (CollectConstants.RETRY_BACKOFF_MILLIS << 1);
        // 保留 300/600ms 生产退避；下界容忍计时偏差，上界为慢速 CI 留足余量。
        assertThat(elapsedMs).isBetween(expectedWaitMs - timeoutMs, TimeUnit.SECONDS.toMillis(20));
    }

    @Test
    @DisplayName("本地 404：保留失败状态和原因，不重试且只获取一次域名限速许可")
    void shouldNotRetryNotFound() throws IOException {
        String url = this.startServer(exchange -> this.respond(exchange, 404, "not found"));

        HttpFetcher.HttpResult result = httpFetcher.get(url, REQUEST_TIMEOUT_MS, MIN_INTERVAL_MS);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(404);
        assertThat(result.getErrorMessage()).contains("404");
        assertThat(result.getAttempts()).isEqualTo(1);
        assertThat(result.isRetryable()).isFalse();
        assertThat(result.getUrl()).isEqualTo(url);
        this.assertRequestsPassedRateLimiter(url, 1);
    }

    @Test
    @DisplayName("本地先返回 500 再恢复 200：第二次成功即停止，每次请求均经过域名限速器")
    void shouldRetryServerErrorAndStopAfterRecovery() throws IOException {
        AtomicInteger responses = new AtomicInteger();
        String url = this.startServer(exchange -> {
            if (responses.incrementAndGet() == 1) {
                this.respond(exchange, 500, "temporary failure");
            } else {
                this.respond(exchange, 200, "recovered");
            }
        });

        HttpFetcher.HttpResult result = httpFetcher.get(url, REQUEST_TIMEOUT_MS, MIN_INTERVAL_MS);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo("recovered");
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getAttempts()).isEqualTo(2);
        assertThat(result.isRetryable()).isFalse();
        assertThat(responses.get()).isEqualTo(2);
        this.assertRequestsPassedRateLimiter(url, 1, 2);
    }

    @Test
    @DisplayName("本地正常返回：保留状态与 UTF-8 正文，GET 请求携带采集器 User-Agent")
    void shouldReturnBodyAndSendCollectorUserAgent() throws IOException {
        AtomicReference<String> userAgent = new AtomicReference<>();
        AtomicReference<String> method = new AtomicReference<>();
        String body = "本地采集响应";
        String url = this.startServer(exchange -> {
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            method.set(exchange.getRequestMethod());
            this.respond(exchange, 200, body);
        });

        HttpFetcher.HttpResult result = httpFetcher.get(url, REQUEST_TIMEOUT_MS, MIN_INTERVAL_MS);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(body);
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getAttempts()).isEqualTo(1);
        assertThat(result.isRetryable()).isFalse();
        assertThat(result.getUrl()).isEqualTo(url);
        assertThat(method.get()).isEqualTo("GET");
        assertThat(userAgent.get()).isEqualTo(CollectConstants.USER_AGENT).isEqualTo(HttpFetcher.USER_AGENT);
        this.assertRequestsPassedRateLimiter(url, 1);
    }

    private String startServer(HttpHandler handler) throws IOException {
        // 固定使用 IPv4 loopback，随机端口避免冲突；三条工作线程允许三次超时响应同时阻塞。
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newFixedThreadPool(CollectConstants.MAX_RETRY_TIMES + 1);
        server.setExecutor(executor);
        server.createContext("/fixture", exchange -> {
            try {
                permitsSeenByRequests.add(acquiredPermits.get());
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/fixture";
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private void assertRequestsPassedRateLimiter(String url, Integer... expectedPermits) {
        verify(rateLimiter, times(expectedPermits.length)).acquire(url, MIN_INTERVAL_MS);
        verifyNoMoreInteractions(rateLimiter);
        assertThat(permitsSeenByRequests).as("每次真实 HTTP 请求到达前都应先取得对应的域名限速许可").containsExactly(expectedPermits);
    }
}
