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

package com.guardfind.server.collect.constant;

import java.time.Duration;

/**
 * 寻亲数据采集常量
 *
 * <p>
 * 采集合规相关的取值集中在此处定义，避免散落到各实现类：User-Agent 明示产品标识与联系入口、robots 缓存 24 小时、
 * 重试上限与退避基数、默认同域间隔与单轮页数上限、原文正文留存期等。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public final class CollectConstants {

    /**
     * 采集器 User-Agent（明示产品标识与联系入口，不得伪造浏览器 UA）
     */
    public static final String USER_AGENT = "GuardFindCollector/1.0 (+https://guardfind.local/contact; collector@guardfind.local)";

    /**
     * robots.txt 缓存键前缀
     */
    public static final String ROBOTS_CACHE_KEY_PREFIX = "COLLECT:ROBOTS:";

    /**
     * robots.txt 缓存时长（24 小时，避免每轮重复请求站方规则文件）
     */
    public static final Duration ROBOTS_CACHE_TTL = Duration.ofHours(24);

    /**
     * 候选审批并发保护键前缀
     */
    public static final String APPROVE_LOCK_KEY_PREFIX = "COLLECT:APPROVE:";

    /**
     * 候选审批并发保护时长（仅用于快速失败，最终一致性由档案表来源唯一键保证）
     */
    public static final Duration APPROVE_LOCK_TTL = Duration.ofMinutes(2);

    /**
     * 首次请求之外的最大重试次数
     */
    public static final int MAX_RETRY_TIMES = 2;

    /**
     * 指数退避基数（毫秒）：第 n 次重试等待 {@code 基数 << (n-1)}
     */
    public static final long RETRY_BACKOFF_MILLIS = 300;

    /**
     * 默认请求超时（毫秒）
     */
    public static final int DEFAULT_TIMEOUT_MILLIS = 10000;

    /**
     * 默认同域抓取间隔（毫秒），配置缺失时兜底
     */
    public static final long DEFAULT_CRAWL_DELAY_MILLIS = 2000;

    /**
     * 默认单轮最大列表页数
     */
    public static final int DEFAULT_MAX_PAGES = 5;

    /**
     * 原文正文留存天数（到期仅清理正文，保留摘要与来源标识）
     */
    public static final int RAW_RETENTION_DAYS = 180;

    /**
     * 批次号格式
     */
    public static final String BATCH_NO_PATTERN = "yyyyMMddHHmmssSSS";

    /**
     * 档案来源：采集
     */
    public static final String SOURCE_TYPE_COLLECT = "COLLECT";

    /**
     * 原文页面类型：列表页
     */
    public static final String PAGE_TYPE_LIST = "LIST";

    /**
     * 原文页面类型：详情页
     */
    public static final String PAGE_TYPE_DETAIL = "DETAIL";

    /**
     * 触发方式：调度任务
     */
    public static final String TRIGGER_JOB = "JOB";

    /**
     * 触发方式：人工触发
     */
    public static final String TRIGGER_MANUAL = "MANUAL";

    /**
     * 来源关联方式：审批生成档案
     */
    public static final String LINK_TYPE_APPROVE = "APPROVE";

    /**
     * 来源关联方式：合并入既有档案
     */
    public static final String LINK_TYPE_MERGE = "MERGE";

    /**
     * 本地样例源标识（不发起任何网络请求）
     */
    public static final String LOCAL_FIXTURE_CODE = "local-fixture";

    /**
     * 本地样例页 classpath 目录
     */
    public static final String FIXTURE_DIR = "collect/fixtures/";

    /**
     * 本地样例源地址前缀（作为 source_url 落库，便于回溯样例文件）
     */
    public static final String FIXTURE_URL_PREFIX = "local://fixtures/";

    /**
     * 错误摘要最大长度（与 {@code gf_collect_log.error_summary} 对齐）
     */
    public static final int ERROR_SUMMARY_MAX_LENGTH = 1000;

    /**
     * 中止原因：robots.txt 未允许抓取
     */
    public static final String ABORT_ROBOTS_DISALLOWED = "ROBOTS_DISALLOWED";

    /**
     * 中止原因：robots.txt 无法获取
     */
    public static final String ABORT_ROBOTS_UNAVAILABLE = "ROBOTS_UNAVAILABLE";

    /**
     * 中止原因：robots.txt 解析失败
     */
    public static final String ABORT_ROBOTS_UNPARSABLE = "ROBOTS_UNPARSABLE";

    private CollectConstants() {
    }
}
