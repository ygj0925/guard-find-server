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

package com.guardfind.server.plaza.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.common.enums.GenderEnum;
import com.guardfind.server.findcase.enums.CaseStatusEnum;
import com.guardfind.server.findcase.enums.UrgencyLevelEnum;
import com.guardfind.server.findcase.mapper.FindCaseAliasMapper;
import com.guardfind.server.findcase.mapper.FindCaseMapper;
import com.guardfind.server.findcase.mapper.SearchHistoryMapper;
import com.guardfind.server.findcase.mapper.UserTrackMapper;
import com.guardfind.server.findcase.model.entity.FindCaseAliasDO;
import com.guardfind.server.findcase.model.entity.FindCaseDO;
import com.guardfind.server.findcase.model.entity.SearchHistoryDO;
import com.guardfind.server.findcase.model.entity.UserTrackDO;
import com.guardfind.server.findcase.model.query.CaseFeedQuery;
import com.guardfind.server.findcase.model.resp.CasePublicResp;
import com.guardfind.server.findcase.service.ContentCheckGateway;
import com.guardfind.server.findcase.support.CaseConstants;
import com.guardfind.server.findcase.support.CaseViewAssembler;
import com.guardfind.server.findcase.support.RegionRepository;
import com.guardfind.server.plaza.enums.FeedSortEnum;
import com.guardfind.server.plaza.mapper.PlazaStatMapper;
import com.guardfind.server.plaza.model.req.TrackReq;
import com.guardfind.server.plaza.model.resp.FeedCursorResp;
import com.guardfind.server.plaza.model.resp.FeedOptionResp;
import com.guardfind.server.plaza.model.resp.PlazaFilterResp;
import com.guardfind.server.plaza.model.resp.PlazaStatisticsResp;
import com.guardfind.server.plaza.service.PlazaService;
import com.guardfind.server.plaza.util.FeedCursor;
import com.guardfind.server.system.service.DictItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.starter.core.constant.StringConstants;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.core.util.CollUtils;
import top.continew.starter.core.util.validation.ValidationUtils;
import top.continew.starter.extension.crud.model.resp.LabelValueResp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 广场业务实现
 *
 * <p>
 * 状态过滤只认 {@link CaseStatusEnum#visiblePublicly()} 与 {@link CaseStatusEnum#foundVisible()} 两个集合，
 * 任何一条流都不允许通过入参放宽可见性；分页一律走游标（排序键 + 主键），不做 offset 深翻。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlazaServiceImpl implements PlazaService {

    /**
     * 字典编码：失踪情形
     */
    private static final String DICT_CASE_TYPE = "gf_case_type";

    /**
     * 字典编码：年龄段
     */
    private static final String DICT_AGE_GROUP = "gf_age_group";

    /**
     * 字典编码：血样采集情况
     */
    private static final String DICT_BLOOD_SAMPLE_STATUS = "gf_blood_sample_status";

    /**
     * 字典编码：与本人关系
     */
    private static final String DICT_RELATION_TYPE = "gf_relation_type";

    /**
     * 别名命中后回查档案的 ID 上限（防止超长 IN 子句）
     */
    private static final int ALIAS_HIT_LIMIT = 500;

    /**
     * 单用户保留的搜索历史条数
     */
    private static final int SEARCH_HISTORY_KEEP = 20;

    private final FindCaseMapper findCaseMapper;
    private final FindCaseAliasMapper findCaseAliasMapper;
    private final SearchHistoryMapper searchHistoryMapper;
    private final UserTrackMapper userTrackMapper;
    private final PlazaStatMapper plazaStatMapper;
    private final CaseViewAssembler caseViewAssembler;
    private final RegionRepository regionRepository;
    private final DictItemService dictItemService;
    private final ContentCheckGateway contentCheckGateway;

    @Override
    public FeedCursorResp<CasePublicResp> feed(String sort,
                                               String provinceCode,
                                               String cityCode,
                                               String keyword,
                                               String cursor,
                                               Integer size) {
        CaseFeedQuery query = new CaseFeedQuery();
        query.setSort(sort);
        query.setProvinceCode(provinceCode);
        query.setCityCode(cityCode);
        query.setKeyword(keyword);
        query.setCursor(cursor);
        query.setSize(size);
        return this.feed(query);
    }

    @Override
    public FeedCursorResp<CasePublicResp> feed(CaseFeedQuery query) {
        return this.query(query, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeedCursorResp<CasePublicResp> search(CaseFeedQuery query) {
        String keyword = StrUtil.trimToEmpty(query.getKeyword());
        ValidationUtils.throwIfBlank(keyword, "搜索关键词不能为空");
        ValidationUtils.throwIf(keyword
            .length() > CaseConstants.MAX_KEYWORD_LENGTH, "搜索关键词长度不能超过 {} 个字符", CaseConstants.MAX_KEYWORD_LENGTH);
        // 敏感词命中即拒，不执行查询
        String reason = contentCheckGateway.check(keyword);
        ValidationUtils.throwIf(StrUtil.isNotBlank(reason), "搜索词不合规：{}", reason);
        query.setKeyword(keyword);
        FeedCursorResp<CasePublicResp> resp = this.query(query, true);
        this.saveSearchHistory(query.getKeyword());
        return resp;
    }

    @Override
    public PlazaFilterResp filters() {
        PlazaFilterResp resp = new PlazaFilterResp();
        resp.setRegions(regionRepository.tree());
        resp.setCaseTypes(this.dictOptions(DICT_CASE_TYPE));
        resp.setAgeGroups(this.dictOptions(DICT_AGE_GROUP));
        resp.setBloodSampleStatuses(this.dictOptions(DICT_BLOOD_SAMPLE_STATUS));
        resp.setRelations(this.dictOptions(DICT_RELATION_TYPE));
        List<FeedOptionResp> genders = new ArrayList<>();
        for (GenderEnum gender : GenderEnum.values()) {
            genders.add(FeedOptionResp.of(gender.name(), gender.getDescription(), gender.getValue()));
        }
        resp.setGenders(genders);
        List<FeedOptionResp> urgencies = new ArrayList<>();
        int order = 1;
        for (UrgencyLevelEnum urgency : UrgencyLevelEnum.values()) {
            urgencies.add(FeedOptionResp.of(urgency.name(), urgency.getDescription(), order++));
        }
        resp.setUrgencyLevels(urgencies);
        return resp;
    }

    @Override
    public PlazaStatisticsResp statistics() {
        LocalDateTime now = LocalDateTime.now();
        PlazaStatisticsResp resp = new PlazaStatisticsResp();
        resp.setTotalCase(this.countTotal());
        resp.setFoundCase(this.count(CaseStatusEnum.foundVisible(), null, null));
        resp.setRecentNewCase(this.count(this.visibleStatuses(), now.minusDays(30), null));
        resp.setTodayLead(plazaStatMapper.countLeadsSince(LocalDate.now().atStartOfDay()));
        return resp;
    }

    @Override
    public void track(TrackReq req) {
        UserTrackDO track = new UserTrackDO();
        Long userId = AppUserContextHolder.getUserId();
        track.setUserId(userId);
        track.setEventType(StrUtil.trimToEmpty(req.getEventType()).toUpperCase());
        track.setTargetType(CaseConstants.TARGET_TYPE_CASE);
        track.setTargetId(req.getTargetId());
        Map<String, Object> extra = new HashMap<>(4);
        extra.put("targetCase", StrUtil.emptyIfNull(req.getTargetCase()));
        extra.put("durationMs", Objects.requireNonNullElse(req.getDurationMs(), 0L));
        track.setExtra(JSONUtil.toJsonStr(extra));
        track.setCreateUser(userId);
        userTrackMapper.insert(track);
    }

    /**
     * 信息流查询主流程
     *
     * @param query     条件
     * @param forSearch 是否为搜索场景（搜索允许在喜报之外的流中命中"待审核被驳回"以外的公开档案）
     * @return 分页结果
     */
    private FeedCursorResp<CasePublicResp> query(CaseFeedQuery query, boolean forSearch) {
        FeedSortEnum sort = this.resolveSort(query.getSort());
        int size = this.resolveSize(query.getSize());
        FeedCursor cursor = FeedCursor.decode(query.getCursor());
        LambdaQueryWrapper<FindCaseDO> wrapper = Wrappers.<FindCaseDO>lambdaQuery()
            .in(FindCaseDO::getStatus, this.statusesOf(sort));
        this.applyRegion(wrapper, sort, query);
        this.applyUrgency(wrapper, sort);
        if (forSearch) {
            this.applySearchFilters(wrapper, query);
        }
        this.applyKeyword(wrapper, query.getKeyword());
        this.applyCursor(wrapper, cursor, sort);
        // 多取一条用于判断是否还有下一页
        // 排序列是 COALESCE 表达式（LambdaQuery 无法表达），由枚举内部拼接，参数无外部输入
        wrapper.last("ORDER BY " + sort.getTimeColumnExpr() + " DESC, id DESC LIMIT " + (size + 1));
        List<FindCaseDO> fetched = findCaseMapper.selectList(wrapper);
        return FeedCursorResp.of(fetched, size, caseDO -> FeedCursor.toSortKey(this
            .sortTimeOf(caseDO, sort)), FindCaseDO::getId, this.caseViewAssembler::toPublicList);
    }

    private FeedSortEnum resolveSort(String sort) {
        if (StrUtil.isBlank(sort)) {
            return FeedSortEnum.LATEST;
        }
        FeedSortEnum feedSort = FeedSortEnum.of(sort);
        ValidationUtils.throwIfNull(feedSort, "排序方式 [{}] 无效", sort);
        return feedSort;
    }

    private int resolveSize(Integer size) {
        if (size == null || size <= 0) {
            return CaseConstants.DEFAULT_FEED_SIZE;
        }
        return Math.min(size, CaseConstants.MAX_FEED_SIZE);
    }

    /**
     * 每条流的可见状态集合（服务端强制，不接受入参覆盖）
     */
    private Set<CaseStatusEnum> statusesOf(FeedSortEnum sort) {
        Set<CaseStatusEnum> statuses = EnumSet.noneOf(CaseStatusEnum.class);
        statuses.addAll(sort.includeFound() ? CaseStatusEnum.foundVisible() : CaseStatusEnum.visiblePublicly());
        return statuses;
    }

    private Set<CaseStatusEnum> visibleStatuses() {
        Set<CaseStatusEnum> statuses = EnumSet.noneOf(CaseStatusEnum.class);
        statuses.addAll(CaseStatusEnum.visiblePublicly());
        return statuses;
    }

    /**
     * 就近流未指定区划时，退回到登录用户的常驻区划
     */
    private void applyRegion(LambdaQueryWrapper<FindCaseDO> wrapper, FeedSortEnum sort, CaseFeedQuery query) {
        String provinceCode = StrUtil.trimToNull(query.getProvinceCode());
        String cityCode = StrUtil.trimToNull(query.getCityCode());
        if (sort == FeedSortEnum.NEARBY && provinceCode == null && cityCode == null) {
            cityCode = AppUserContextHolder.getContext() == null
                ? null
                : AppUserContextHolder.getContext().getCityCode();
        }
        wrapper.eq(provinceCode != null, FindCaseDO::getProvinceCode, provinceCode)
            .eq(cityCode != null, FindCaseDO::getCityCode, cityCode);
    }

    /**
     * 紧急流：只保留仍在黄金 72 小时内的档案
     */
    private void applyUrgency(LambdaQueryWrapper<FindCaseDO> wrapper, FeedSortEnum sort) {
        if (sort != FeedSortEnum.URGENT) {
            return;
        }
        wrapper.eq(FindCaseDO::getUrgencyLevel, UrgencyLevelEnum.URGENT)
            .ge(FindCaseDO::getMissingTime, LocalDateTime.now().minusHours(UrgencyLevelEnum.GOLDEN_HOURS));
    }

    private void applySearchFilters(LambdaQueryWrapper<FindCaseDO> wrapper, CaseFeedQuery query) {
        String caseType = StrUtil.trimToNull(query.getCaseType());
        String ageGroup = StrUtil.trimToNull(query.getAgeGroup());
        String bloodSampleStatus = StrUtil.trimToNull(query.getBloodSampleStatus());
        GenderEnum gender = this.parseGender(query.getGender());
        ValidationUtils.throwIf(gender == null && StrUtil.isNotBlank(query.getGender()), "性别取值无效");
        ValidationUtils.throwIf(query.getYearFrom() != null && query.getYearTo() != null && query.getYearFrom() > query
            .getYearTo(), "失踪年份起始不能大于截止");
        wrapper.eq(caseType != null, FindCaseDO::getCaseType, caseType)
            .eq(ageGroup != null, FindCaseDO::getAgeGroup, ageGroup)
            .eq(bloodSampleStatus != null, FindCaseDO::getBloodSampleStatus, bloodSampleStatus)
            .eq(gender != null, FindCaseDO::getGender, gender)
            .ge(query.getYearFrom() != null, FindCaseDO::getMissingTime, this.yearStart(query.getYearFrom()))
            .le(query.getYearTo() != null, FindCaseDO::getMissingTime, this.yearEnd(query.getYearTo()));
    }

    /**
     * 关键词匹配：姓名、别名、失踪地点文本、体貌特征（一期走 MySQL 索引，不引入 ES）
     */
    private void applyKeyword(LambdaQueryWrapper<FindCaseDO> wrapper, String keyword) {
        String word = StrUtil.trimToNull(keyword);
        if (word == null) {
            return;
        }
        ValidationUtils.throwIf(word
            .length() > CaseConstants.MAX_KEYWORD_LENGTH, "搜索关键词长度不能超过 {} 个字符", CaseConstants.MAX_KEYWORD_LENGTH);
        List<Long> aliasCaseIds = this.listAliasHitCaseIds(word);
        wrapper.and(outer -> {
            outer.like(FindCaseDO::getName, word)
                .or()
                .like(FindCaseDO::getFeatures, word)
                .or()
                .like(FindCaseDO::getAddress, word)
                .or()
                .like(FindCaseDO::getAccent, word);
            if (CollUtil.isNotEmpty(aliasCaseIds)) {
                outer.or().in(FindCaseDO::getId, aliasCaseIds);
            }
        });
    }

    private List<Long> listAliasHitCaseIds(String keyword) {
        List<FindCaseAliasDO> aliases = findCaseAliasMapper.lambdaQuery()
            .select(FindCaseAliasDO::getCaseId)
            .like(FindCaseAliasDO::getName, keyword)
            .last("LIMIT " + ALIAS_HIT_LIMIT)
            .list();
        return CollUtils.mapToList(aliases, FindCaseAliasDO::getCaseId);
    }

    /**
     * 游标边界：排序键小于上一页末值，或相等时主键小于上一页末值
     */
    private void applyCursor(LambdaQueryWrapper<FindCaseDO> wrapper, FeedCursor cursor, FeedSortEnum sort) {
        if (cursor == null) {
            return;
        }
        LocalDateTime boundary = FeedCursor.toLocalDateTime(cursor.sortKey());
        String timeColumn = sort.getTimeColumnExpr();
        wrapper.and(outer -> outer.apply(timeColumn + " < {0}", boundary)
            .or()
            .apply("(" + timeColumn + " = {0} AND id < {1})", boundary, cursor.id()));
    }

    private LocalDateTime sortTimeOf(FindCaseDO caseDO, FeedSortEnum sort) {
        if (sort == FeedSortEnum.FOUND) {
            return ObjectUtil.defaultIfNull(caseDO.getFoundTime(), this.publishTimeOf(caseDO));
        }
        return this.publishTimeOf(caseDO);
    }

    private LocalDateTime publishTimeOf(FindCaseDO caseDO) {
        return ObjectUtil.defaultIfNull(caseDO.getPublishTime(), caseDO.getCreateTime());
    }

    private GenderEnum parseGender(String gender) {
        if (StrUtil.isBlank(gender)) {
            return null;
        }
        try {
            return GenderEnum.valueOf(gender.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("性别取值无效");
        }
    }

    private LocalDateTime yearStart(Integer year) {
        return year == null ? null : LocalDate.of(year, 1, 1).atStartOfDay();
    }

    private LocalDateTime yearEnd(Integer year) {
        return year == null ? null : LocalDate.of(year, 12, 31).atTime(23, 59, 59);
    }

    private long count(Set<CaseStatusEnum> statuses, LocalDateTime since, LocalDateTime until) {
        return Objects.requireNonNullElse(findCaseMapper.selectCount(Wrappers.<FindCaseDO>lambdaQuery()
            .in(CollUtil.isNotEmpty(statuses), FindCaseDO::getStatus, statuses)
            .ne(CollUtil.isEmpty(statuses), FindCaseDO::getStatus, CaseStatusEnum.ERASED)
            .ge(since != null, FindCaseDO::getCreateTime, since)
            .le(until != null, FindCaseDO::getCreateTime, until)), 0L);
    }

    /**
     * 累计建档数（排除行使删除权后仅剩空壳的档案）
     */
    private long countTotal() {
        return this.count(null, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearSearchHistory() {
        Long userId = AppUserContextHolder.getUserIdRequired();
        searchHistoryMapper.delete(Wrappers.<SearchHistoryDO>lambdaQuery().eq(SearchHistoryDO::getUserId, userId));
    }

    /**
     * 字典项 → 筛选项（顺序以字典 sort 为准，前端不硬编码）
     */
    private List<FeedOptionResp> dictOptions(String dictCode) {
        List<LabelValueResp> items = dictItemService.listByDictCode(dictCode);
        List<FeedOptionResp> options = new ArrayList<>();
        if (CollUtil.isEmpty(items)) {
            return options;
        }
        int order = 1;
        for (LabelValueResp item : items) {
            options.add(FeedOptionResp.of(Objects.toString(item.getValue(), StringConstants.EMPTY), StrUtil
                .emptyIfNull(item.getLabel()), order++));
        }
        return options;
    }

    /**
     * 登录用户保留最近若干条搜索历史（同一关键词只留最新一条）
     */
    private void saveSearchHistory(String keyword) {
        Long userId = AppUserContextHolder.getUserId();
        if (userId == null || StrUtil.isBlank(keyword)) {
            return;
        }
        SearchHistoryDO exist = searchHistoryMapper.lambdaQuery()
            .eq(SearchHistoryDO::getUserId, userId)
            .eq(SearchHistoryDO::getKeyword, keyword)
            .orderByDesc(SearchHistoryDO::getCreateTime)
            .last("LIMIT 1")
            .one();
        LocalDateTime now = LocalDateTime.now();
        if (exist == null) {
            SearchHistoryDO history = new SearchHistoryDO();
            history.setUserId(userId);
            history.setKeyword(keyword);
            history.setCreateUser(userId);
            history.setCreateTime(now);
            history.setUpdateUser(userId);
            history.setUpdateTime(now);
            searchHistoryMapper.insert(history);
        } else {
            SearchHistoryDO update = new SearchHistoryDO();
            update.setId(exist.getId());
            update.setCreateTime(now);
            update.setUpdateTime(now);
            update.setUpdateUser(userId);
            searchHistoryMapper.updateById(update);
        }
        // 截断历史：仅保留最近 SEARCH_HISTORY_KEEP 条
        List<SearchHistoryDO> historyList = searchHistoryMapper.lambdaQuery()
            .eq(SearchHistoryDO::getUserId, userId)
            .orderByDesc(SearchHistoryDO::getCreateTime)
            .orderByDesc(SearchHistoryDO::getId)
            .list();
        if (historyList.size() > SEARCH_HISTORY_KEEP) {
            List<Long> expiredIds = CollUtils.mapToList(historyList.subList(SEARCH_HISTORY_KEEP, historyList
                .size()), SearchHistoryDO::getId);
            searchHistoryMapper.deleteByIds(expiredIds);
        }
    }
}
