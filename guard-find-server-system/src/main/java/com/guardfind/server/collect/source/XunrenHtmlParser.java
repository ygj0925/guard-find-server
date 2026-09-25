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

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import com.guardfind.server.collect.model.CandidateDraft;
import com.guardfind.server.collect.model.RawPage;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 寻亲页解析器（列表页 + 详情页）
 *
 * <p>
 * 站外页面是"粗排版的标签行"：{@code <a href="/xunren/view/143036">姓名</a>} 组成列表，详情是一条条
 * {@code <b>性别</b>：男} 这样的标签行，且顺序不稳定、字段常缺失、HTML 常脏。因此这里不依赖 DOM 层级，而是把
 * {@code <br>} 折行还原成文本行后按标签取值——标签缺失即返回 null，绝不抛异常，也绝不因顺序变化串字段。
 * 选择器与标签名集中在此处定义，真实源与本地样例源共用同一份解析规则。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Component
public class XunrenHtmlParser {

    /**
     * 列表页详情条目选择器
     */
    public static final String LIST_LINK_SELECTOR = "a[href*=xunren/view]";

    /**
     * 详情页编号提取（列表链接与浏览器地址栏均适用；样例源编号含中划线，故放宽字符集）
     */
    public static final Pattern DETAIL_ID_PATTERN = Pattern.compile("xunren/view/([\\w.\\-]+)");

    /**
     * 详情页标签：姓名
     */
    public static final String LABEL_NAME = "姓名";

    /**
     * 详情页标签：类别
     */
    public static final String LABEL_CATEGORY = "类别";

    /**
     * 详情页标签：编号
     */
    public static final String LABEL_SOURCE_ID = "编号";

    /**
     * 详情页标签：性别
     */
    public static final String LABEL_GENDER = "性别";

    /**
     * 详情页标签：生日
     */
    public static final String LABEL_BIRTHDAY = "生日";

    /**
     * 详情页标签：年龄
     */
    public static final String LABEL_AGE = "年龄";

    /**
     * 详情页标签：出生地（籍贯），按页面实际写法提供变体
     */
    public static final List<String> LABEL_BIRTHPLACE = List.of("出生地(籍贯)", "出生地（籍贯）", "出生地", "籍贯");

    /**
     * 详情页标签：失踪时身高
     */
    public static final String LABEL_HEIGHT = "失踪时身高";

    /**
     * 详情页标签：失踪日期
     */
    public static final String LABEL_MISSING_DATE = "失踪日期";

    /**
     * 详情页标签：失踪地点（列表页与详情页两种写法）
     */
    public static final List<String> LABEL_MISSING_REGION = List.of("失踪地点", "失踪地址");

    /**
     * 详情页标签：特征描述
     */
    public static final String LABEL_FEATURES = "特征描述";

    /**
     * 详情页标签：发生经过
     */
    public static final String LABEL_NARRATIVE = "发生经过";

    /**
     * 详情页标签：家庭背景及线索资料
     */
    public static final List<String> LABEL_FAMILY_CLUES = List.of("家庭背景及线索资料", "家庭背景", "线索资料");

    /**
     * 详情页标签：其他说明
     */
    public static final String LABEL_OTHER_NOTE = "其他说明";

    /**
     * 详情页标签：添加日期
     */
    public static final String LABEL_ADDED_DATE = "添加日期";

    /**
     * 详情页标签：寻亲联系人
     */
    public static final String LABEL_CONTACT = "寻亲联系人";

    /**
     * 别名标签（页面偶见）
     */
    public static final List<String> LABEL_ALIASES = List.of("别名", "小名", "昵称");

    /**
     * 头像图片选择器（按优先级取第一张命中的肖像图）
     */
    public static final List<String> PORTRAIT_SELECTORS = List
        .of("#dedepic img", "#viewphoto img", ".photo img", ".detail img[src*=upload]", "img[src*=upload]");

    /**
     * 非肖像图的地址特征（站点头部图、图标、水印不参与取图）
     */
    private static final List<String> IMAGE_EXCLUDE_KEYWORDS = List
        .of("logo", "icon", "banner", "ad_", "weixin", "qrcode");

    /**
     * 全部标签的扁平集合，用于识别"下一字段起始行"，从而支持字段错位的页面
     */
    private static final Set<String> ALL_LABELS = new LinkedHashSet<>();

    static {
        ALL_LABELS.add(LABEL_NAME);
        ALL_LABELS.add(LABEL_CATEGORY);
        ALL_LABELS.add(LABEL_SOURCE_ID);
        ALL_LABELS.add(LABEL_GENDER);
        ALL_LABELS.add(LABEL_BIRTHDAY);
        ALL_LABELS.add(LABEL_AGE);
        ALL_LABELS.addAll(LABEL_BIRTHPLACE);
        ALL_LABELS.add(LABEL_HEIGHT);
        ALL_LABELS.add(LABEL_MISSING_DATE);
        ALL_LABELS.addAll(LABEL_MISSING_REGION);
        ALL_LABELS.add(LABEL_FEATURES);
        ALL_LABELS.add(LABEL_NARRATIVE);
        ALL_LABELS.addAll(LABEL_FAMILY_CLUES);
        ALL_LABELS.add(LABEL_OTHER_NOTE);
        ALL_LABELS.add(LABEL_ADDED_DATE);
        ALL_LABELS.add(LABEL_CONTACT);
        ALL_LABELS.addAll(LABEL_ALIASES);
    }

    /**
     * 从列表页解析详情条目地址（保持页面顺序、去重）
     *
     * @param baseUrl 站点根地址，用于把相对链接补成可访问绝对地址
     * @param listUrl 当前列表页地址
     * @param html    列表页 HTML
     * @return 详情页绝对地址列表
     */
    public List<String> parseListPageUrls(String baseUrl, String listUrl, String html) {
        List<String> urls = new ArrayList<>();
        if (StrUtil.isBlank(html)) {
            return urls;
        }
        try {
            Document doc = Jsoup.parse(html, StrUtil.blankToDefault(listUrl, StrUtil
                .blankToDefault(baseUrl, "about:blank")));
            Elements links = doc.select(LIST_LINK_SELECTOR);
            Set<String> distinct = new LinkedHashSet<>();
            for (Element link : links) {
                String href = StrUtil.trim(link.attr("href"));
                if (StrUtil.isBlank(href) || !ReUtil.isMatch(DETAIL_ID_PATTERN, href)) {
                    continue;
                }
                String absolute = this.toAbsolute(baseUrl, href);
                if (StrUtil.isNotBlank(absolute)) {
                    distinct.add(absolute);
                }
            }
            urls.addAll(distinct);
        } catch (Exception e) {
            log.warn("列表页解析失败：{}，原因：{}", listUrl, e.getMessage());
        }
        return urls;
    }

    /**
     * 解析详情页地址中的源侧编号
     *
     * @param url 详情页地址
     * @return 编号，无法识别时为 null
     */
    public String parseSourceId(String url) {
        return ReUtil.get(DETAIL_ID_PATTERN, StrUtil.emptyIfNull(url), 1);
    }

    /**
     * 解析详情页为候选草稿
     *
     * @param html      详情页 HTML
     * @param sourceUrl 详情页地址
     * @param raw       原文快照
     * @return 草稿；整页未解析出姓名与编号时为 {@link Optional#empty()}（脏页面跳过，不断批）
     */
    public Optional<CandidateDraft> parseDetail(String html, String sourceUrl, RawPage raw) {
        if (StrUtil.isBlank(html)) {
            return Optional.empty();
        }
        try {
            List<String> lines = this.textLines(html);
            CandidateDraft draft = new CandidateDraft();
            draft.setRaw(raw);
            draft.setSourceUrl(sourceUrl);
            draft.setName(this.labelValue(lines, List.of(LABEL_NAME)));
            draft.setCategoryText(this.labelValue(lines, List.of(LABEL_CATEGORY)));
            draft.setSourceId(StrUtil.blankToDefault(this.labelValue(lines, List.of(LABEL_SOURCE_ID)), this
                .parseSourceId(sourceUrl)));
            draft.setGenderText(this.labelValue(lines, List.of(LABEL_GENDER)));
            draft.setBirthdayText(this.labelValue(lines, List.of(LABEL_BIRTHDAY)));
            draft.setAgeText(this.labelValue(lines, List.of(LABEL_AGE)));
            draft.setRegionText(this.labelValue(lines, LABEL_BIRTHPLACE));
            draft.setAddressText(this.labelValue(lines, LABEL_MISSING_REGION));
            draft.setHeightText(this.labelValue(lines, List.of(LABEL_HEIGHT)));
            draft.setMissingDateText(this.labelValue(lines, List.of(LABEL_MISSING_DATE)));
            draft.setFeaturesText(this.labelValue(lines, List.of(LABEL_FEATURES)));
            draft.setNarrativeText(this.labelValue(lines, List.of(LABEL_NARRATIVE)));
            draft.setFamilyCluesText(this.labelValue(lines, LABEL_FAMILY_CLUES));
            draft.setOtherNoteText(this.labelValue(lines, List.of(LABEL_OTHER_NOTE)));
            draft.setContactText(this.labelValue(lines, List.of(LABEL_CONTACT)));
            draft.setAliases(this.splitAliases(this.labelValue(lines, LABEL_ALIASES)));
            draft.setCoverUrl(this.parsePortraitUrl(html, sourceUrl));
            if (draft.isUnresolved()) {
                log.info("详情页未解析出有效字段，跳过：{}", sourceUrl);
                return Optional.empty();
            }
            return Optional.of(draft);
        } catch (Exception e) {
            log.warn("详情页解析异常，跳过：{}，原因：{}", sourceUrl, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * HTML → 文本行：还原换行、剔除脚本样式与多余空白，标签缺失时表现为"没有该行"而非异常
     */
    private List<String> textLines(String html) {
        String prepared = html.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("(?i)</(p|div|li|tr|h[1-6])>", "\n");
        Document doc = Jsoup.parse(prepared);
        doc.select("script,style,noscript,iframe").remove();
        String text = doc.body() == null ? doc.wholeText() : doc.body().wholeText();
        List<String> lines = new ArrayList<>();
        for (String line : StrUtil.split(text, '\n')) {
            // 站方页面残留 &nbsp;（解析后为 \u00a0）、全角空格与制表符，统一为普通空格后再按标签定位
            String value = line.replace('　', ' ').replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
            value = value.replace("&nbsp;", " ").replace("&#160;", " ").trim();
            if (StrUtil.isNotBlank(value)) {
                lines.add(value);
            }
        }
        return lines;
    }

    /**
     * 按标签取值：命中"标签：值"起始行后，向下收集直到遇到下一个标签行或空行
     */
    private String labelValue(List<String> lines, List<String> labels) {
        if (CollUtil.isEmpty(lines)) {
            return null;
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            for (String label : labels) {
                String remainder = this.stripLabelPrefix(line, label);
                if (remainder == null) {
                    continue;
                }
                StringBuilder value = new StringBuilder(remainder);
                for (int j = i + 1; j < lines.size(); j++) {
                    String next = lines.get(j);
                    if (this.startsWithAnyLabel(next)) {
                        break;
                    }
                    value.append(' ').append(next);
                }
                String result = value.toString().replaceAll("\\s{2,}", " ").trim();
                return StrUtil.blankToDefault(result, null);
            }
        }
        return null;
    }

    /**
     * 去掉标签前缀：仅接受"标签：值"与"标签 值"两种写法，避免"年龄"命中"年龄差"这类误取
     */
    private String stripLabelPrefix(String line, String label) {
        if (!line.startsWith(label) || line.length() == label.length()) {
            return null;
        }
        String raw = line.substring(label.length());
        char separator = raw.charAt(0);
        if (separator == ' ' || separator == '\t' || separator == '　') {
            raw = raw.trim();
            return this.stripColon(raw);
        }
        if (separator == '：' || separator == ':') {
            return raw.substring(1).trim();
        }
        return null;
    }

    private String stripColon(String value) {
        if (value.startsWith("：") || value.startsWith(":")) {
            return value.substring(1).trim();
        }
        return value;
    }

    private boolean startsWithAnyLabel(String line) {
        for (String label : ALL_LABELS) {
            if (line.startsWith(label)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取详情页肖像图（绝对地址）
     */
    private String parsePortraitUrl(String html, String sourceUrl) {
        try {
            Document doc = Jsoup.parse(html, StrUtil.blankToDefault(sourceUrl, "about:blank"));
            for (String selector : PORTRAIT_SELECTORS) {
                for (Element img : doc.select(selector)) {
                    String url = this.candidateImage(img);
                    if (url != null) {
                        return url;
                    }
                }
            }
            for (Element img : doc.select("img[src]")) {
                String url = this.candidateImage(img);
                if (url != null) {
                    return url;
                }
            }
        } catch (Exception e) {
            log.debug("肖像图解析失败：{}", e.getMessage());
        }
        return null;
    }

    private String candidateImage(Element img) {
        String src = StrUtil.trim(img.attr("abs:src"));
        if (StrUtil.isBlank(src)) {
            src = StrUtil.trim(img.attr("src"));
        }
        if (StrUtil.isBlank(src)) {
            return null;
        }
        String lower = src.toLowerCase();
        for (String exclude : IMAGE_EXCLUDE_KEYWORDS) {
            if (lower.contains(exclude)) {
                return null;
            }
        }
        String widthAttr = ReUtil.get("\\d+", StrUtil.emptyIfNull(img.attr("width")), 0);
        if (StrUtil.isNotBlank(widthAttr) && Integer.parseInt(widthAttr) < 60) {
            // 头像缩略图与表情图不入库
            return null;
        }
        return src;
    }

    /**
     * 别名切分（顿号、逗号、斜杠、空白均可）
     */
    private List<String> splitAliases(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        Set<String> aliases = new LinkedHashSet<>();
        for (String part : text.split("[、,，/;；\\s]+")) {
            if (StrUtil.isNotBlank(part)) {
                aliases.add(part.trim());
            }
        }
        return aliases.isEmpty() ? null : new ArrayList<>(aliases);
    }

    /**
     * 相对地址补全为绝对地址
     */
    private String toAbsolute(String baseUrl, String href) {
        String value = href.trim();
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("local://")) {
            return value;
        }
        String base = StrUtil.emptyToDefault(baseUrl, "");
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return value.startsWith("/") ? base + value : base + "/" + value;
    }
}
