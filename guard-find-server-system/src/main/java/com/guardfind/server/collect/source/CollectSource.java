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

package com.guardfind.server.collect.source;

import com.guardfind.server.collect.model.CandidateDraft;
import com.guardfind.server.collect.model.SourceConfig;

import java.util.List;
import java.util.Optional;

/**
 * 采集源 SPI
 *
 * <p>
 * 采集主流程只依赖本接口：列页取地址、详情页取草稿。新增一个站点只需实现本接口（返回唯一 {@link #code()}）并在
 * {@code gf_collect_source} 增加一行配置，编排器、去重、落库、审核链路零改动。
 * </p>
 *
 * <p>
 * 实现约定：①不得自行放宽 robots 与限速（合规判定在编排器，限速在 {@code HttpFetcher}）；②解析不出关键字段时
 * 返回 {@link Optional#empty()} 而不是抛异常，让单页失败只影响单页；③不得采集非公开内容、不得绕过登录与验证码。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public interface CollectSource {

    /**
     * 源标识，与 {@code gf_collect_source.code} 一一对应
     *
     * @return 源标识
     */
    String code();

    /**
     * 是否需要 robots 判定
     *
     * <p>
     * 默认 {@code true}：任何联网站点都受 robots 约束，且不提供关闭入口。仅不发起网络请求的本地样例源可返回
     * {@code false}（无站方可打扰，合规判定对它不适用）。
     * </p>
     *
     * @return true：本轮采集前必须通过 robots 判定
     */
    default boolean robotsRequired() {
        return true;
    }

    /**
     * 拉取指定列表页中的详情条目地址
     *
     * @param config 源运行配置
     * @param page   页码（从 1 开始）
     * @return 详情页地址列表（有序、去重）；该页无条目时返回空列表
     */
    List<String> fetchListPageUrls(SourceConfig config, int page);

    /**
     * 抓取并解析详情页
     *
     * @param config 源运行配置
     * @param url    详情页地址（由 {@link #fetchListPageUrls} 给出）
     * @return 候选草稿（含原文快照）；页面不可用或无有效字段时为 {@link Optional#empty()}
     */
    Optional<CandidateDraft> fetchDetail(SourceConfig config, String url);
}
