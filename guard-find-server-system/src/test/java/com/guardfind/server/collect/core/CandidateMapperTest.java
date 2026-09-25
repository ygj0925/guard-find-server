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

import com.guardfind.server.collect.enums.CandidateStatusEnum;
import com.guardfind.server.collect.model.CandidateDraft;
import com.guardfind.server.collect.model.SourceConfig;
import com.guardfind.server.collect.model.entity.CollectCandidateDO;
import com.guardfind.server.collect.source.LocalFixtureSource;
import com.guardfind.server.collect.source.XunrenHtmlParser;
import com.guardfind.server.common.enums.GenderEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 候选归一化与样例页解析单元测试（不加载 Spring 上下文、不触网）
 *
 * <p>
 * 覆盖规格"解析器从真实结构样例产出候选"与"归一化与去重"：字段一一对应、日期到日、区划到码、性别与情形枚举化、
 * 姓名与别名规范化、完整度与 dedupeKey 稳定。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
class CandidateMapperTest {

    private final LocalFixtureSource fixtureSource = new LocalFixtureSource(new XunrenHtmlParser());
    private final CandidateMapper candidateMapper = new CandidateMapper();
    private final SourceConfig config = new SourceConfig();

    @BeforeEach
    void setUp() {
        config.setId(2L);
        config.setCode(LocalFixtureSource.CODE);
        config.setName("本地样例源");
        config.setBaseUrl("local://fixtures");
        config.setCategoryIds(List.of("default"));
        config.setCrawlDelayMs(0);
        config.setMaxPages(1);
        config.setRequestTimeoutMs(1000);
    }

    private CollectCandidateDO mapFixture(String sourceId) {
        Optional<CandidateDraft> draft = fixtureSource.fetchDetail(config, "local://fixtures/" + sourceId);
        assertThat(draft).as("样例页 %s 应解析出草稿", sourceId).isPresent();
        CollectCandidateDO candidate = candidateMapper.map(draft.get(), config, "20260924090000123");
        candidate.setId(1L);
        return candidate;
    }

    @Test
    @DisplayName("正常详情页：来源标识与链接可回溯，抓取时间与原文摘要随行留存")
    void shouldMapNormalDetailFieldByField() {
        CollectCandidateDO candidate = this.mapFixture("detail-normal");

        assertThat(candidate.getSourceCode()).isEqualTo(LocalFixtureSource.CODE);
        assertThat(candidate.getSourceId()).isEqualTo("detail-normal");
        assertThat(candidate.getSourceUrl()).isEqualTo("local://fixtures/detail-normal.html");
        assertThat(candidate.getFetchTime()).isNotNull();
        assertThat(candidate.getCategoryText()).isEqualTo("被拐");
    }

    @Test
    @DisplayName("正常详情页：姓名/性别/生日/失踪日期/身高/区划/情形均归一化")
    void shouldNormalizeFields() {
        CollectCandidateDO candidate = this.mapFixture("detail-normal");

        assertThat(candidate.getName()).isEqualTo("张三合");
        assertThat(candidate.getAliases()).containsExactly("小合", "合子");
        assertThat(candidate.getGender()).isEqualTo(GenderEnum.MALE);
        assertThat(candidate.getBirthday()).isEqualTo(LocalDate.of(2001, 3, 12));
        assertThat(candidate.getAgeAtMissing()).isEqualTo(24);
        assertThat(candidate.getMissingTime()).isEqualTo(LocalDateTime.of(2004, 8, 15, 0, 0));
        assertThat(candidate.getHeight()).isEqualTo(120);
        assertThat(candidate.getProvinceCode()).isEqualTo("130000");
        assertThat(candidate.getCityCode()).isEqualTo("130100");
        assertThat(candidate.getRegionText()).isEqualTo("河北省石家庄市");
        assertThat(candidate.getAddress()).isEqualTo("桥西区胜利南街 88 号样例小学门口");
        assertThat(candidate.getCaseType()).isEqualTo("TRAFFICKING");
        assertThat(candidate.getBloodSampleStatus()).isEqualTo("UNKNOWN");
        assertThat(candidate.getFeatures()).contains("胎记");
        assertThat(candidate.getNarrative()).contains("2004");
        assertThat(candidate.getFamilyClues()).contains("血样");
        assertThat(candidate.getContactText()).contains("13800000000");
        assertThat(candidate.getCoverUrl()).contains("fix-90001.jpg");
        assertThat(candidate.getFetchTime()).isNotNull();
        assertThat(candidate.getRawId()).isNull();
        assertThat(candidate.getStatus()).isEqualTo(CandidateStatusEnum.PENDING);
        assertThat(candidate.getBatchNo()).isEqualTo("20260924090000123");
        assertThat(candidate.getDedupeKey()).hasSize(64);
        assertThat(candidate.getCompleteness()).isGreaterThan(new BigDecimal("0.80"));
    }

    @Test
    @DisplayName("缺失失踪日期：该条仍可入库，失踪时间为 null（不臆造日期）")
    void shouldKeepCandidateWhenMissingDate() {
        CollectCandidateDO candidate = this.mapFixture("detail-missing-date");

        assertThat(candidate.getName()).isEqualTo("王五合");
        assertThat(candidate.getMissingTime()).isNull();
        assertThat(candidate.getBirthday()).isEqualTo(LocalDate.of(2010, 6, 1));
        assertThat(candidate.getCityCode()).isEqualTo("411300");
        assertThat(candidate.getCaseType()).isEqualTo("LOST");
        assertThat(candidate.getDedupeKey()).hasSize(64);
    }

    @Test
    @DisplayName("字段错位页：解析不依赖标签顺序，且与正序页产生同一指纹")
    void shouldParseShuffledLabelsAndYieldSameDedupeKey() {
        CollectCandidateDO normal = this.mapFixture("detail-normal");
        CollectCandidateDO shuffled = this.mapFixture("detail-shuffled-labels");

        assertThat(shuffled.getName()).isEqualTo("张三合");
        assertThat(shuffled.getGender()).isEqualTo(GenderEnum.MALE);
        assertThat(shuffled.getMissingTime()).isEqualTo(LocalDateTime.of(2004, 8, 15, 0, 0));
        assertThat(shuffled.getCityCode()).isEqualTo("130100");
        assertThat(shuffled.getDedupeKey()).isEqualTo(normal.getDedupeKey());
        // 乱序页字段更少，完整度必须更低，供收敛时挑出主候选
        assertThat(shuffled.getCompleteness()).isLessThan(normal.getCompleteness());
    }

    @Test
    @DisplayName("脏 HTML：不抛异常，能取多少取多少；米制身高与全角日期被正确归一")
    void shouldTolerateDirtyHtml() {
        CollectCandidateDO candidate = this.mapFixture("detail-dirty-html");

        assertThat(candidate.getName()).isEqualTo("李四合");
        assertThat(candidate.getGender()).isEqualTo(GenderEnum.FEMALE);
        assertThat(candidate.getBirthday()).isEqualTo(LocalDate.of(1998, 7, 2));
        assertThat(candidate.getMissingTime()).isEqualTo(LocalDateTime.of(1998, 12, 3, 0, 0));
        assertThat(candidate.getHeight()).isEqualTo(162);
        assertThat(candidate.getProvinceCode()).isEqualTo("510000");
        assertThat(candidate.getCityCode()).isEqualTo("510700");
        assertThat(candidate.getAddress()).contains("涪城区");
        assertThat(candidate.getCaseType()).isEqualTo("NO_CONTACT");
        assertThat(candidate.getFeatures()).contains("结巴");
    }

    @Test
    @DisplayName("404 与空白页：解析不出有效字段即返回空，供编排器记失败明细")
    void shouldReturnEmptyForUnusablePages() {
        assertThat(fixtureSource.fetchDetail(config, "local://fixtures/404")).isEmpty();
        assertThat(fixtureSource.fetchDetail(config, "local://fixtures/empty")).isEmpty();
        assertThat(fixtureSource.fetchDetail(config, "local://fixtures/not-exists")).isEmpty();
    }

    @Test
    @DisplayName("列表页：条目地址解析保持顺序且去重，样例源不触网")
    void shouldParseListPageInOrder() {
        List<String> urls = fixtureSource.fetchListPageUrls(config, 1);

        assertThat(urls)
            .containsExactly("local://fixtures/detail-normal", "local://fixtures/detail-shuffled-labels", "local://fixtures/detail-missing-date", "local://fixtures/detail-dirty-html", "local://fixtures/404", "local://fixtures/empty");
        assertThat(fixtureSource.fetchListPageUrls(config, 2)).isEmpty();
    }

    @Test
    @DisplayName("dedupeKey 对排版差异稳定：日期写法、姓名空白、全半角变化不产生新指纹")
    void dedupeKeyShouldBeStableAcrossFormatting() {
        CandidateDraft plain = this.draft("张三合", "男", "2004-08-15", "河北省石家庄市桥西区示例路 1 号");
        CandidateDraft spaced = this.draft("张 三 合", "男性", "2004年8月15日", "河北 石家庄 桥西区示例路1号");
        CandidateDraft slashed = this.draft("张三合", "M", "2004/8/15", "石家庄市桥西区示例路1号");

        CollectCandidateDO a = candidateMapper.map(plain, config, "B1");
        CollectCandidateDO b = candidateMapper.map(spaced, config, "B1");
        CollectCandidateDO c = candidateMapper.map(slashed, config, "B1");

        assertThat(b.getName()).isEqualTo(a.getName());
        assertThat(c.getDedupeKey()).isEqualTo(a.getDedupeKey()).isEqualTo(b.getDedupeKey());
        // 日期或地区不同 → 指纹不同（避免把两个人收敛成一条）
        CollectCandidateDO other = candidateMapper.map(this.draft("张三合", "男", "2005-08-15", "河北省石家庄市"), config, "B1");
        assertThat(other.getDedupeKey()).isNotEqualTo(a.getDedupeKey());
    }

    @Test
    @DisplayName("完整度按填充字段比例计算，用于挑选主候选")
    void completenessShouldReflectFilledFields() {
        CollectCandidateDO sparse = candidateMapper.map(this.draft("张三合", null, null, null), config, "B1");
        CollectCandidateDO rich = candidateMapper.map(this.draft("张三合", "男", "2004-08-15", "河北省石家庄市"), config, "B1");

        assertThat(sparse.getCompleteness()).isLessThan(rich.getCompleteness());
        assertThat(rich.getCompleteness()).isLessThanOrEqualTo(BigDecimal.ONE);
        assertThat(rich.getMissingTime()).isEqualTo(LocalDateTime.of(2004, 8, 15, 0, 0));
    }

    private CandidateDraft draft(String name, String gender, String missingDate, String address) {
        CandidateDraft draft = new CandidateDraft();
        draft.setSourceId("manual-" + name + "-" + missingDate + "-" + address);
        draft.setSourceUrl("local://fixtures/manual");
        draft.setName(name);
        draft.setGenderText(gender);
        draft.setMissingDateText(missingDate);
        draft.setAddressText(address);
        return draft;
    }
}
