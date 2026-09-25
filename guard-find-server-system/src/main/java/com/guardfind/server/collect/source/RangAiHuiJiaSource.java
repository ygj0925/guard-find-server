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

import cn.hutool.core.util.StrUtil;
import com.guardfind.server.collect.constant.CollectConstants;
import com.guardfind.server.collect.model.CandidateDraft;
import com.guardfind.server.collect.model.RawPage;
import com.guardfind.server.collect.model.SourceConfig;
import com.guardfind.server.collect.core.HttpFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.continew.starter.core.exception.BusinessException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 公开寻亲库采集源（关爱回家）
 *
 * <p>
 * 列表 {@code GET {base}/xunren/category/{categoryId}?p-page={page}}，详情 {@code GET {base}/xunren/view/{id}}，
 * 解析规则见 {@link XunrenHtmlParser}（选择器与标签名集中在其中）。是否可采由编排器依据 robots 判定，
 * 本实现只在被放行后被调用，且所有请求经 {@link HttpFetcher} 走同域限速与合规 UA。
 * </p>
 *
 * <p>
 * <b>上线前置条件</b>：{@code gf_collect_source.enabled} 默认关闭，须取得站方书面授权或法务确认后由运营显式开启；
 * 未授权前调度任务会跳过本源。本类不做任何绕过登录、验证码或接口签名的能力。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RangAiHuiJiaSource implements CollectSource {

    /**
     * 源标识
     */
    public static final String CODE = "rangaihuijia";

    private final HttpFetcher httpFetcher;
    private final XunrenHtmlParser parser;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<String> fetchListPageUrls(SourceConfig config, int page) {
        List<String> result = new ArrayList<>();
        for (String categoryId : config.getCategoryIds()) {
            String listUrl = config.buildListUrl(categoryId, page);
            if (StrUtil.isBlank(listUrl)) {
                throw new BusinessException("采集源 [%s] 未配置列表路径模板".formatted(config.getCode()));
            }
            HttpFetcher.HttpResult listResult = this.request(listUrl, config);
            if (!listResult.isSuccess()) {
                throw new BusinessException("列表页抓取失败：%s（%s）".formatted(listUrl, listResult.getErrorMessage()));
            }
            for (String url : parser.parseListPageUrls(config.getBaseUrl(), listUrl, listResult.getBody())) {
                if (!result.contains(url)) {
                    result.add(url);
                }
            }
        }
        return result;
    }

    @Override
    public Optional<CandidateDraft> fetchDetail(SourceConfig config, String url) {
        HttpFetcher.HttpResult result = this.request(url, config);
        if (!result.isSuccess()) {
            log.warn("详情页抓取失败，跳过：{}，原因：{}", url, result.getErrorMessage());
            return Optional.empty();
        }
        RawPage raw = new RawPage();
        raw.setSourceCode(config.getCode());
        raw.setSourceId(parser.parseSourceId(url));
        raw.setUrl(url);
        raw.setPageType(CollectConstants.PAGE_TYPE_DETAIL);
        raw.setContent(result.getBody());
        raw.setFetchTime(LocalDateTime.now());
        raw.fillSha256();
        Optional<CandidateDraft> draft = parser.parseDetail(result.getBody(), url, raw);
        draft.ifPresent(item -> {
            if (StrUtil.isBlank(item.getSourceId())) {
                item.setSourceId(raw.getSourceId());
            }
        });
        return draft;
    }

    private HttpFetcher.HttpResult request(String url, SourceConfig config) {
        return httpFetcher.get(url, config.getRequestTimeoutMs(), config.getCrawlDelayMs());
    }
}
