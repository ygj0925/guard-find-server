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

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.util.StrUtil;
import com.guardfind.server.collect.constant.CollectConstants;
import com.guardfind.server.collect.model.CandidateDraft;
import com.guardfind.server.collect.model.RawPage;
import com.guardfind.server.collect.model.SourceConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 本地样例采集源
 *
 * <p>
 * 读取 classpath 下 {@code collect/fixtures/*.html} 的合成样例页，走与真实源<b>完全相同</b>的
 * {@link XunrenHtmlParser} 解析与 {@code CandidateMapper} 归一化链路，用于无外网环境的 CI、演示与回归。
 * </p>
 *
 * <p>
 * 该源 MUST NOT 发起任何网络请求：地址形态为 {@code local://fixtures/{sourceId}.html}，
 * 样例文件即"站点"。样例中的人名、地址、照片均为合成数据，不含真实失踪人员信息。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalFixtureSource implements CollectSource {

    /**
     * 源标识
     */
    public static final String CODE = CollectConstants.LOCAL_FIXTURE_CODE;

    private final XunrenHtmlParser parser;

    @Override
    public String code() {
        return CODE;
    }

    /**
     * 本地样例不发起任何网络请求，robots 判定不适用（真实联网源 MUST NOT 覆写为 false）
     */
    @Override
    public boolean robotsRequired() {
        return false;
    }

    @Override
    public List<String> fetchListPageUrls(SourceConfig config, int page) {
        String html = this.readFixture("list-page-" + page);
        if (StrUtil.isBlank(html)) {
            return List.of();
        }
        // 样例页复用真实源的选择器，链接形态一致（/xunren/view/{id}），id 即样例文件名
        List<String> urls = new ArrayList<>();
        for (String url : parser.parseListPageUrls(config.getBaseUrl(), null, html)) {
            String sourceId = parser.parseSourceId(url);
            if (StrUtil.isNotBlank(sourceId)) {
                urls.add(CollectConstants.FIXTURE_URL_PREFIX + sourceId);
            }
        }
        return urls;
    }

    @Override
    public Optional<CandidateDraft> fetchDetail(SourceConfig config, String url) {
        String sourceId = this.resolveSourceId(url);
        if (StrUtil.isBlank(sourceId)) {
            log.warn("样例地址无法解析出编号，跳过：{}", url);
            return Optional.empty();
        }
        String html = this.readFixture(sourceId);
        if (StrUtil.isBlank(html)) {
            log.warn("样例页缺失或为空，跳过：{}", sourceId);
            return Optional.empty();
        }
        String fixtureUrl = CollectConstants.FIXTURE_URL_PREFIX + sourceId + ".html";
        RawPage raw = new RawPage();
        raw.setSourceCode(CODE);
        raw.setSourceId(sourceId);
        raw.setUrl(fixtureUrl);
        raw.setPageType(CollectConstants.PAGE_TYPE_DETAIL);
        raw.setContent(html);
        raw.setFetchTime(LocalDateTime.now());
        raw.fillSha256();
        Optional<CandidateDraft> draft = parser.parseDetail(html, fixtureUrl, raw);
        draft.ifPresent(item -> {
            if (StrUtil.isBlank(item.getSourceId())) {
                item.setSourceId(sourceId);
            }
            item.setSourceUrl(fixtureUrl);
        });
        return draft;
    }

    /**
     * 从样例地址中取编号，兼容 {@code local://fixtures/{id}} 与 {@code .../{id}.html}
     */
    private String resolveSourceId(String url) {
        String value = StrUtil.subAfter(StrUtil.emptyIfNull(url), "/", true);
        value = StrUtil.subBefore(value, "?", false);
        return StrUtil.removeSuffix(value, ".html");
    }

    /**
     * 读取 classpath 样例文件（缺失或空文件返回 null，由调用方按失败明细处理，不抛异常）
     */
    private String readFixture(String name) {
        String path = CollectConstants.FIXTURE_DIR + name + ".html";
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (resource.getUrl() == null) {
                return null;
            }
            return StrUtil.blankToDefault(IoUtil.read(resource.getStream(), StandardCharsets.UTF_8), null);
        } catch (Exception e) {
            log.debug("样例页不可用：{}（{}）", path, e.getMessage());
            return null;
        }
    }
}
