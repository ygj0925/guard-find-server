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

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import com.guardfind.server.collect.enums.CandidateStatusEnum;
import com.guardfind.server.collect.model.CandidateDraft;
import com.guardfind.server.collect.model.SourceConfig;
import com.guardfind.server.common.enums.GenderEnum;
import com.guardfind.server.collect.model.entity.CollectCandidateDO;
import com.guardfind.server.findcase.util.CaseDedupeKeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 候选归一化映射器
 *
 * <p>
 * 采集源的"页面怎么写"与档案的"系统怎么存"之间只此一道桥：日期归一到日、区划文本映射为行政区划码、性别与情形
 * 枚举化、姓名与别名去空白与全半角差异，最后算出完整度与去重指纹（复用档案域
 * {@link CaseDedupeKeyUtil}，保证"站外采到的"与"家属自己发的"用同一把钥匙收敛）。
 * </p>
 *
 * <p>
 * 归一化 <b>只做加法不做臆断</b>：区划未命中时编码留空但保留 {@code regionText}，日期不可识别时为 {@code null}，
 * 任何一步都不抛异常——脏页面由编排器记为失败明细，不许拖垮整批。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Component
public class CandidateMapper {

    /**
     * 完整度评估字段（寻亲必要信息，与档案提交校验口径一致）
     */
    private static final List<String> COMPLETENESS_FIELDS = List
        .of("name", "gender", "birthday", "missingTime", "provinceCode", "cityCode", "districtCode", "address", "height", "features", "narrative", "familyClues", "coverUrl");

    /**
     * 年月日（分隔符兼容 - / . 与中文年月日）
     */
    private static final Pattern DAY_PATTERN = Pattern
        .compile("(\\d{4})\\s*[-/年.]\\s*(\\d{1,2})\\s*[-/月.]\\s*(\\d{1,2})");

    /**
     * 年月
     */
    private static final Pattern MONTH_PATTERN = Pattern.compile("(\\d{4})\\s*[-/年.]\\s*(\\d{1,2})\\s*[月]?");

    /**
     * 仅年份
     */
    private static final Pattern YEAR_PATTERN = Pattern.compile("(\\d{4})\\s*年?");

    /**
     * 身高（数字，可带小数与单位）
     */
    private static final Pattern HEIGHT_PATTERN = Pattern.compile("(\\d{1,3}(?:\\.\\d{1,2})?)\\s*(cm|厘米|公分|m|米)?");

    /**
     * 年龄
     */
    private static final Pattern AGE_PATTERN = Pattern.compile("(\\d{1,3})\\s*岁?");

    /**
     * 失踪情形关键字（字典 gf_case_type）
     */
    private static final List<List<String>> CASE_TYPE_KEYWORDS = List.of(List
        .of("TRAFFICKING", "被拐", "拐卖", "拐骗", "偷抱"), List.of("LOST_ELDERLY", "老人走失", "老年走失", "阿尔茨海默"), List
            .of("NO_CONTACT", "失联", "杳无音信"), List.of("LOST", "走失", "失踪"), List.of("MISSING", "其他"));

    /**
     * 默认失踪情形（无法判定时按"其他"，审核人再细化）
     */
    private static final String DEFAULT_CASE_TYPE = "MISSING";

    /**
     * 血样采集情况（站外页面不含该信息）
     */
    private static final String DEFAULT_BLOOD_SAMPLE = "UNKNOWN";

    /**
     * 把候选草稿归一化为候选实体
     *
     * @param draft   草稿（页面原文字段）
     * @param config  源运行配置
     * @param batchNo 批次号
     * @return 候选实体（未落库，rawId 由编排器补齐）
     */
    public CollectCandidateDO map(CandidateDraft draft, SourceConfig config, String batchNo) {
        CollectCandidateDO candidate = new CollectCandidateDO();
        candidate.setSourceCode(config.getCode());
        candidate.setSourceId(StrUtil.trimToNull(draft.getSourceId()));
        candidate.setSourceUrl(StrUtil.trimToNull(draft.getSourceUrl()));
        candidate.setFetchTime(draft.getRaw() == null ? null : draft.getRaw().getFetchTime());
        candidate.setCategoryText(this.trimTo(draft.getCategoryText(), 64));
        candidate.setName(this.normalizeName(draft.getName()));
        candidate.setAliases(this.normalizeAliases(draft.getAliases(), candidate.getName()));
        candidate.setGender(this.parseGender(draft.getGenderText()));
        candidate.setBirthday(parseDate(draft.getBirthdayText()));
        candidate.setAgeAtMissing(this.parseAge(draft.getAgeText()));
        candidate.setMissingTime(parseDateAsTime(draft.getMissingDateText()));
        RegionMatcher.RegionHit hit = RegionMatcher.match(this.regionSource(draft));
        candidate.setProvinceCode(hit.provinceCode());
        candidate.setCityCode(hit.cityCode());
        candidate.setDistrictCode(hit.districtCode());
        this.fillRegionAndAddress(candidate, draft, hit);
        candidate.setHeight(this.parseHeight(draft.getHeightText()));
        candidate.setFeatures(this.trimTo(draft.getFeaturesText(), 500));
        candidate.setNarrative(this.trimTo(draft.getNarrativeText(), null));
        candidate.setFamilyClues(this.trimTo(draft.getFamilyCluesText(), null));
        candidate.setOtherNote(this.trimTo(draft.getOtherNoteText(), 1000));
        candidate.setContactText(this.trimTo(draft.getContactText(), 255));
        candidate.setCoverUrl(this.trimTo(draft.getCoverUrl(), 500));
        candidate.setCaseType(parseCaseType(draft.getCategoryText()));
        candidate.setBloodSampleStatus(DEFAULT_BLOOD_SAMPLE);
        candidate.setCompleteness(completeness(candidate));
        candidate.setDedupeKey(this.buildDedupeKey(candidate));
        candidate.setStatus(CandidateStatusEnum.PENDING);
        candidate.setBatchNo(batchNo);
        return candidate;
    }

    /**
     * 区划识别取文本：优先"失踪地址"（含区划前缀），退化到籍贯
     */
    private String regionSource(CandidateDraft draft) {
        return StrUtil.isNotBlank(draft.getAddressText()) ? draft.getAddressText() : draft.getRegionText();
    }

    /**
     * 区划原文与详细地址分离：命中的区划拼进 regionText，剩余部分进 address；未命中则原文照留
     */
    private void fillRegionAndAddress(CollectCandidateDO candidate, CandidateDraft draft, RegionMatcher.RegionHit hit) {
        String raw = StrUtil.trimToNull(this.regionSource(draft));
        if (hit.provinceCode() == null) {
            candidate.setRegionText(null);
            candidate.setAddress(this.trimTo(raw, 500));
            return;
        }
        candidate.setRegionText(this.trimTo(RegionMatcher.joinHit(hit), 128));
        candidate.setAddress(this.trimTo(this.stripRegionPrefix(raw, hit), 500));
    }

    /**
     * 去掉已识别的区划前缀，避免"河北河北邯郸"式重复
     */
    private String stripRegionPrefix(String raw, RegionMatcher.RegionHit hit) {
        if (StrUtil.isBlank(raw)) {
            return raw;
        }
        List<String> codes = new ArrayList<>();
        codes.add(hit.provinceCode());
        codes.add(hit.cityCode());
        codes.add(hit.districtCode());
        String result = raw.trim();
        for (String code : codes) {
            String name = RegionMatcher.nameOf(code);
            if (StrUtil.isBlank(name)) {
                continue;
            }
            for (String variant : new String[] {name, RegionMatcher.shortNameOf(name)}) {
                if (result.startsWith(variant)) {
                    result = result.substring(variant.length()).trim();
                    break;
                }
            }
        }
        return StrUtil.blankToDefault(result, raw.trim());
    }

    /**
     * 姓名规范化：去空白、全角转半角、去结尾异常符号（与去重指纹口径一致）
     *
     * @param name 原始姓名
     * @return 规范化姓名，空值返回 null
     */
    public String normalizeName(String name) {
        if (StrUtil.isBlank(name)) {
            return null;
        }
        String value = StrUtil.cleanBlank(toHalfWidth(name));
        // 站外页面偶见"XXX同志"“XXX（小）”这类附加写法，仅剥离括号补充与结尾称谓，保留少数民族姓名的间隔号
        value = ReUtil.replaceAll(value, "[（(][^（()）]*[)）]$", "");
        value = ReUtil.replaceAll(value, "(同志|小朋友|同学)$", "");
        return this.trimTo(value, 64);
    }

    /**
     * 别名规范化：去空白与全半角差异，剔除与正名重复项
     */
    public List<String> normalizeAliases(List<String> aliases, String name) {
        if (CollUtil.isEmpty(aliases)) {
            return null;
        }
        Set<String> result = new LinkedHashSet<>();
        for (String alias : aliases) {
            String value = StrUtil.cleanBlank(toHalfWidth(StrUtil.emptyIfNull(alias)));
            if (StrUtil.isBlank(value) || StrUtil.equals(value, StrUtil.emptyIfNull(name))) {
                continue;
            }
            result.add(StrUtil.subPre(value, 32));
        }
        return result.isEmpty() ? null : new ArrayList<>(result);
    }

    /**
     * 性别归一化（男/女/M/F/1/2 等写法），无法判定时返回 null（不臆断）
     *
     * @param text 性别原文
     * @return 性别枚举
     */
    public GenderEnum parseGender(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        String value = toHalfWidth(text).trim().toLowerCase();
        if (value.contains("男") || value.startsWith("m") || "1".equals(value)) {
            return GenderEnum.MALE;
        }
        if (value.contains("女") || value.startsWith("f") || "2".equals(value)) {
            return GenderEnum.FEMALE;
        }
        return null;
    }

    /**
     * 失踪情形归一化（字典 gf_case_type）
     *
     * @param text 类别原文
     * @return 情形编码，无法判定时为 MISSING
     */
    public static String parseCaseType(String text) {
        if (StrUtil.isBlank(text)) {
            return DEFAULT_CASE_TYPE;
        }
        String value = text.trim();
        for (List<String> group : CASE_TYPE_KEYWORDS) {
            String code = group.get(0);
            for (int i = 1; i < group.size(); i++) {
                if (value.contains(group.get(i))) {
                    return code;
                }
            }
        }
        return DEFAULT_CASE_TYPE;
    }

    /**
     * 日期归一到日（兼容 2003-05-01、2003/5/1、2003年5月1日、2003.5.1、2003年5月、2003年）
     *
     * @param text 日期原文
     * @return 日期，无法识别时为 null
     */
    public static LocalDate parseDate(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        String value = toHalfWidth(text);
        Matcher day = DAY_PATTERN.matcher(value);
        if (day.find()) {
            return toLocalDate(day.group(1), day.group(2), day.group(3));
        }
        Matcher month = MONTH_PATTERN.matcher(value);
        if (month.find()) {
            return toLocalDate(month.group(1), month.group(2), "1");
        }
        Matcher year = YEAR_PATTERN.matcher(value);
        if (year.find()) {
            return toLocalDate(year.group(1), "1", "1");
        }
        return null;
    }

    /**
     * 日期原文归一时间为日精度（00:00）
     *
     * @param text 日期原文
     * @return 时间，无法识别时为 null
     */
    public static LocalDateTime parseDateAsTime(String text) {
        LocalDate date = parseDate(text);
        return date == null ? null : date.atStartOfDay();
    }

    /**
     * 身高归一化为厘米整数（支持 165、165cm、165 厘米、1.62m；小于 5 的数值按米制换算）
     *
     * @param text 身高原文
     * @return 厘米，无法识别时为 null
     */
    public Integer parseHeight(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        Matcher matcher = HEIGHT_PATTERN.matcher(toHalfWidth(text));
        if (!matcher.find()) {
            return null;
        }
        double number = NumberUtil.parseDouble(matcher.group(1));
        int height = number < 5 ? (int)Math.round(number * 100) : (int)Math.round(number);
        return height <= 0 || height > 250 ? null : height;
    }

    /**
     * 年龄归一化
     */
    private Integer parseAge(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        Matcher matcher = AGE_PATTERN.matcher(toHalfWidth(text));
        if (!matcher.find()) {
            return null;
        }
        int age = NumberUtil.parseInt(matcher.group(1));
        return age < 0 || age > 120 ? null : age;
    }

    /**
     * 计算字段完整度（0-1，两位小数）
     *
     * @param candidate 候选
     * @return 完整度
     */
    public static BigDecimal completeness(CollectCandidateDO candidate) {
        int filled = 0;
        for (String field : COMPLETENESS_FIELDS) {
            if (isFilled(candidate, field)) {
                filled++;
            }
        }
        return BigDecimal.valueOf(filled)
            .divide(BigDecimal.valueOf(COMPLETENESS_FIELDS.size()), 2, RoundingMode.HALF_UP);
    }

    private static boolean isFilled(CollectCandidateDO candidate, String field) {
        return switch (field) {
            case "name" -> StrUtil.isNotBlank(candidate.getName());
            case "gender" -> candidate.getGender() != null && candidate.getGender() != GenderEnum.UNKNOWN;
            case "birthday" -> candidate.getBirthday() != null;
            case "missingTime" -> candidate.getMissingTime() != null;
            case "provinceCode" -> StrUtil.isNotBlank(candidate.getProvinceCode());
            case "cityCode" -> StrUtil.isNotBlank(candidate.getCityCode());
            case "districtCode" -> StrUtil.isNotBlank(candidate.getDistrictCode());
            case "address" -> StrUtil.isNotBlank(candidate.getAddress());
            case "height" -> candidate.getHeight() != null;
            case "features" -> StrUtil.isNotBlank(candidate.getFeatures());
            case "narrative" -> StrUtil.isNotBlank(candidate.getNarrative());
            case "familyClues" -> StrUtil.isNotBlank(candidate.getFamilyClues());
            case "coverUrl" -> StrUtil.isNotBlank(candidate.getCoverUrl());
            default -> false;
        };
    }

    /**
     * 去重指纹：规范化姓名 + 性别 + 失踪日期（到日）+ 区划到市，与档案域同规则
     */
    public String buildDedupeKey(CollectCandidateDO candidate) {
        Integer genderValue = candidate.getGender() == null ? null : candidate.getGender().getValue();
        return CaseDedupeKeyUtil.build(candidate.getName(), genderValue, candidate.getMissingTime(), candidate
            .getProvinceCode(), candidate.getCityCode());
    }

    private static LocalDate toLocalDate(String year, String month, String day) {
        try {
            int y = NumberUtil.parseInt(year);
            int m = NumberUtil.parseInt(month);
            int d = NumberUtil.parseInt(day);
            if (y < 1900 || y > LocalDate.now().getYear() + 1 || m < 1 || m > 12 || d < 1 || d > 31) {
                return null;
            }
            // 页面偶见"2003-02-31"这类非法日期，钳制到当月末，避免整条被判为不可解析
            LocalDate first = LocalDate.of(y, m, 1);
            int lastDay = first.lengthOfMonth();
            return LocalDate.of(y, m, Math.min(d, lastDay));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 全角转半角（含全角空格），消除站方录入习惯带来的差异
     */
    public static String toHalfWidth(String text) {
        if (StrUtil.isBlank(text)) {
            return StrUtil.emptyToDefault(text, "");
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c == '　') {
                builder.append(' ');
            } else if (c >= '＀' && c <= '｠') {
                builder.append((char)(c - 65248));
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private String trimTo(String text, Integer maxLength) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        String value = text.replaceAll("\\s{2,}", " ").trim();
        return maxLength == null ? value : StrUtil.subPre(value, maxLength);
    }
}
