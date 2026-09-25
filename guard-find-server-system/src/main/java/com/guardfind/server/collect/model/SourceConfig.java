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

package com.guardfind.server.collect.model;

import com.guardfind.server.collect.constant.CollectConstants;
import com.guardfind.server.collect.model.entity.CollectSourceDO;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 采集源运行配置
 *
 * <p>
 * 由 {@code gf_collect_source} 派生的运行期视图，负责把路径模板渲染成具体 URL，使采集实现无需关心数据库实体。
 * 缺失项按 {@link CollectConstants} 中的合规默认值兜底（间隔、页数、超时）。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
public class SourceConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 源配置 ID
     */
    private Long id;

    /**
     * 源标识
     */
    private String code;

    /**
     * 源名称
     */
    private String name;

    /**
     * 站点根地址（不含结尾斜杠）
     */
    private String baseUrl;

    /**
     * 列表路径模板
     */
    private String listPathTpl;

    /**
     * 详情路径模板
     */
    private String detailPathTpl;

    /**
     * 栏目/分类标识
     */
    private List<String> categoryIds = new ArrayList<>();

    /**
     * 同域抓取间隔（毫秒），已按站点 robots 的 Crawl-delay 上调
     */
    private long crawlDelayMs;

    /**
     * 单轮最大列表页数
     */
    private int maxPages;

    /**
     * 请求超时（毫秒）
     */
    private int requestTimeoutMs;

    /**
     * 由源配置实体构建运行期配置
     *
     * @param source 源配置实体
     * @return 运行期配置
     */
    public static SourceConfig of(CollectSourceDO source) {
        SourceConfig config = new SourceConfig();
        config.setId(source.getId());
        config.setCode(source.getCode());
        config.setName(source.getName());
        config.setBaseUrl(trimTrailingSlash(source.getBaseUrl()));
        config.setListPathTpl(source.getListPathTpl());
        config.setDetailPathTpl(source.getDetailPathTpl());
        config.setCategoryIds(source.getCategoryIds() == null
            ? new ArrayList<>()
            : new ArrayList<>(source.getCategoryIds()));
        config.setCrawlDelayMs(source.getCrawlDelayMs() == null || source.getCrawlDelayMs() < 0
            ? CollectConstants.DEFAULT_CRAWL_DELAY_MILLIS
            : source.getCrawlDelayMs());
        config.setMaxPages(source.getMaxPages() == null || source.getMaxPages() <= 0
            ? CollectConstants.DEFAULT_MAX_PAGES
            : source.getMaxPages());
        config.setRequestTimeoutMs(source.getRequestTimeoutMs() == null || source.getRequestTimeoutMs() <= 0
            ? CollectConstants.DEFAULT_TIMEOUT_MILLIS
            : source.getRequestTimeoutMs());
        return config;
    }

    /**
     * 渲染列表页地址
     *
     * @param categoryId 栏目标识
     * @param page       页码（从 1 开始）
     * @return 完整地址；未配置模板时返回 null
     */
    public String buildListUrl(String categoryId, int page) {
        if (listPathTpl == null || listPathTpl.isBlank()) {
            return null;
        }
        return render(listPathTpl, categoryId, null, page);
    }

    /**
     * 渲染详情页地址
     *
     * @param sourceId 源侧编号
     * @return 完整地址；未配置模板时返回 null
     */
    public String buildDetailUrl(String sourceId) {
        if (detailPathTpl == null || detailPathTpl.isBlank()) {
            return null;
        }
        return render(detailPathTpl, null, sourceId, 1);
    }

    private String render(String tpl, String categoryId, String sourceId, int page) {
        String path = tpl.replace("{categoryId}", categoryId == null ? "" : categoryId)
            .replace("{sourceId}", sourceId == null ? "" : sourceId)
            .replace("{page}", String.valueOf(page));
        String base = baseUrl == null ? "" : baseUrl;
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return path.startsWith("/") ? base + path : base + "/" + path;
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        String result = url.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
