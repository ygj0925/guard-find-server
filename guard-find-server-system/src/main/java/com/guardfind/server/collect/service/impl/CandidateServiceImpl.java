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

package com.guardfind.server.collect.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.guardfind.server.collect.constant.CollectConstants;
import com.guardfind.server.collect.enums.CandidateStatusEnum;
import com.guardfind.server.collect.mapper.CollectCandidateMapper;
import com.guardfind.server.collect.mapper.CollectEraseLogMapper;
import com.guardfind.server.collect.mapper.CollectLogMapper;
import com.guardfind.server.collect.mapper.CollectRawMapper;
import com.guardfind.server.collect.model.entity.CollectCandidateDO;
import com.guardfind.server.collect.model.entity.CollectEraseLogDO;
import com.guardfind.server.collect.model.entity.CollectLogDO;
import com.guardfind.server.collect.model.entity.CollectRawDO;
import com.guardfind.server.collect.model.query.CandidateQuery;
import com.guardfind.server.collect.model.query.CollectLogQuery;
import com.guardfind.server.collect.model.req.CandidateRejectReq;
import com.guardfind.server.collect.model.req.CollectEraseReq;
import com.guardfind.server.collect.model.resp.CandidateResp;
import com.guardfind.server.collect.model.resp.CollectEraseResp;
import com.guardfind.server.collect.model.resp.CollectLogResp;
import com.guardfind.server.collect.service.CandidateService;
import com.guardfind.server.common.enums.GenderEnum;
import com.guardfind.server.findcase.enums.AgeGroupEnum;
import com.guardfind.server.findcase.enums.CaseOperatorTypeEnum;
import com.guardfind.server.findcase.enums.CaseStatusEnum;
import com.guardfind.server.findcase.enums.UrgencyLevelEnum;
import com.guardfind.server.findcase.mapper.CaseSourceLinkMapper;
import com.guardfind.server.findcase.mapper.CaseStatusLogMapper;
import com.guardfind.server.findcase.mapper.FindCaseMapper;
import com.guardfind.server.findcase.model.entity.CaseSourceLinkDO;
import com.guardfind.server.findcase.model.entity.CaseStatusLogDO;
import com.guardfind.server.findcase.model.entity.FindCaseDO;
import com.guardfind.server.findcase.support.CaseConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.starter.cache.redisson.util.RedisUtils;
import top.continew.starter.core.constant.StringConstants;
import top.continew.starter.core.util.validation.CheckUtils;
import top.continew.starter.core.util.validation.ValidationUtils;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 采集候选审核业务实现
 *
 * <p>
 * 审批的幂等由三层兜住：①候选状态与 {@code caseId} 快路径；②按 {@code (sourceType, sourceCode, sourceId)}
 * 回查既有档案（与 {@code gf_find_case.uk_source} 唯一键同口径）；③插入撞唯一键时回查并返回同一档案。
 * 并发两次审批因此只会得到一条档案、同一个档案号。
 * </p>
 *
 * <p>
 * 采集页面的"寻亲联系人"原文只留在候选表里供审核核对，<b>不随审批写入对外档案</b>：站外号码未经权利人同意，
 * 发布即越界。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateServiceImpl implements CandidateService {

    /**
     * 状态轨迹理由码：采集审批入库
     */
    private static final String REASON_CODE_APPROVE = "COLLECT_APPROVE";

    /**
     * 状态轨迹理由码：来源撤回
     */
    private static final String REASON_CODE_ERASE = "COLLECT_ERASE";

    private final CollectCandidateMapper candidateMapper;
    private final CollectRawMapper rawMapper;
    private final CollectLogMapper logMapper;
    private final CollectEraseLogMapper eraseLogMapper;
    private final FindCaseMapper findCaseMapper;
    private final CaseSourceLinkMapper caseSourceLinkMapper;
    private final CaseStatusLogMapper caseStatusLogMapper;

    @Override
    public PageResp<CandidateResp> page(CandidateQuery query, PageQuery pageQuery) {
        CandidateQuery condition = query == null ? new CandidateQuery() : query;
        LambdaQueryWrapper<CollectCandidateDO> wrapper = Wrappers.<CollectCandidateDO>lambdaQuery()
            .eq(condition.getStatus() != null, CollectCandidateDO::getStatus, condition.getStatus())
            .eq(StrUtil.isNotBlank(condition.getSourceCode()), CollectCandidateDO::getSourceCode, StrUtil.trim(condition
                .getSourceCode()))
            .eq(StrUtil.isNotBlank(condition.getProvinceCode()), CollectCandidateDO::getProvinceCode, condition
                .getProvinceCode())
            .eq(StrUtil.isNotBlank(condition.getCityCode()), CollectCandidateDO::getCityCode, condition.getCityCode())
            .eq(StrUtil.isNotBlank(condition.getBatchNo()), CollectCandidateDO::getBatchNo, condition.getBatchNo())
            .and(StrUtil.isNotBlank(condition.getKeyword()), w -> w.like(CollectCandidateDO::getName, condition
                .getKeyword())
                .or()
                .like(CollectCandidateDO::getFeatures, condition.getKeyword())
                .or()
                .like(CollectCandidateDO::getAddress, condition.getKeyword())
                .or()
                .like(CollectCandidateDO::getRegionText, condition.getKeyword()))
            .orderByDesc(CollectCandidateDO::getCompleteness)
            .orderByDesc(CollectCandidateDO::getId);
        Page<CollectCandidateDO> page = candidateMapper.selectPage(new Page<>(pageQuery.getPage(), pageQuery
            .getSize()), wrapper);
        return PageResp.build(page, CandidateResp.class);
    }

    @Override
    public CandidateResp get(Long id) {
        CollectCandidateDO candidate = this.getCandidate(id);
        CandidateResp resp = this.toResp(candidate);
        CollectRawDO raw = candidate.getRawId() == null ? null : rawMapper.selectById(candidate.getRawId());
        if (raw != null) {
            resp.setRawSha256(raw.getSha256());
            resp.setFetchTime(raw.getFetchedAt());
        }
        resp.setSimilarCaseIds(this.listSimilarCaseIds(candidate));
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long approve(Long id, Long operatorId) {
        CollectCandidateDO candidate = this.getCandidate(id);
        // 快路径：已审批过直接返回既有档案，重复点击不产生第二条档案
        if (candidate.getStatus() == CandidateStatusEnum.APPROVED && candidate.getCaseId() != null) {
            return candidate.getCaseId();
        }
        CheckUtils.throwIf(candidate.getStatus() == CandidateStatusEnum.ERASED, "候选已清除，不能再审批");
        CheckUtils.throwIf(candidate.getStatus() == CandidateStatusEnum.MERGED && candidate
            .getCaseId() != null, "候选已合并到档案 [{}]，无需重复审批", candidate.getCaseId());
        CheckUtils.throwIf(candidate
            .getStatus() == CandidateStatusEnum.DUPLICATED, "该候选已被收敛到主候选 [{}]，请审批主候选或改用合并", candidate.getPrimaryId());
        ValidationUtils.throwIf(StrUtil.isBlank(candidate.getName()), "候选缺少姓名，不能入库为寻人档案");
        ValidationUtils.throwIf(StrUtil.isBlank(candidate.getSourceId()), "候选缺少来源编号，不能入库");
        this.guardConcurrentApproval(id);
        // 并发/重复审批的收敛点：按来源唯一键回查，已有档案即返回
        FindCaseDO existing = this.findCaseBySource(candidate);
        if (existing != null) {
            this.markApproved(candidate, existing.getId(), operatorId);
            return existing.getId();
        }
        // 站内已有同一指纹档案（家属自己发布的）→ 提示合并，不新建重复档案
        List<Long> similarCaseIds = this.listSimilarCaseIds(candidate);
        CheckUtils.throwIf(CollUtil.isNotEmpty(similarCaseIds), "站内已存在同一去重指纹的档案（ID：%s），请改用合并而不是新建", similarCaseIds
            .stream()
            .map(String::valueOf)
            .collect(Collectors.joining("、")));
        FindCaseDO caseDO = this.buildCase(candidate, operatorId);
        try {
            findCaseMapper.insert(caseDO);
        } catch (DuplicateKeyException e) {
            // 两个请求同时通过上面的回查：撞唯一键的一方回查既有档案，最终仍只有一条
            FindCaseDO concurrent = this.findCaseBySource(candidate);
            CheckUtils.throwIfNull(concurrent, "审批失败：来源档案唯一键冲突但未能定位既有档案");
            this.markApproved(candidate, concurrent.getId(), operatorId);
            return concurrent.getId();
        }
        this.saveSourceLink(candidate, caseDO.getId(), CollectConstants.LINK_TYPE_APPROVE, operatorId);
        this.saveStatusLog(caseDO.getId(), null, CaseStatusEnum.PUBLISHED, REASON_CODE_APPROVE, "采集候选审批入库（来源：%s）"
            .formatted(StrUtil.emptyIfNull(candidate.getSourceUrl())), operatorId);
        this.markApproved(candidate, caseDO.getId(), operatorId);
        log.info("采集候选 [{}] 审批入库为档案 [{}]", id, caseDO.getId());
        return caseDO.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long merge(Long id, Long caseId, Long operatorId) {
        CollectCandidateDO candidate = this.getCandidate(id);
        CheckUtils.throwIf(candidate.getStatus() == CandidateStatusEnum.ERASED, "候选已清除，不能合并");
        FindCaseDO target = findCaseMapper.selectById(caseId);
        CheckUtils.throwIfNull(target, "目标档案不存在或已删除");
        CheckUtils.throwIf(CaseStatusEnum.ERASED == target.getStatus(), "目标档案已删除，不能合并");
        // 只追加来源关联：原发布人、发布时间与联系方式一律不动
        CaseSourceLinkDO link = caseSourceLinkMapper.selectOne(Wrappers.<CaseSourceLinkDO>lambdaQuery()
            .eq(CaseSourceLinkDO::getCaseId, caseId)
            .eq(CaseSourceLinkDO::getCandidateId, id)
            .last("LIMIT 1"));
        if (link == null) {
            this.saveSourceLink(candidate, caseId, CollectConstants.LINK_TYPE_MERGE, operatorId);
        }
        candidateMapper.lambdaUpdate()
            .set(CollectCandidateDO::getStatus, CandidateStatusEnum.MERGED)
            .set(CollectCandidateDO::getCaseId, caseId)
            .set(CollectCandidateDO::getPrimaryId, null)
            .set(CollectCandidateDO::getUpdateUser, operatorId)
            .eq(CollectCandidateDO::getId, id)
            .update();
        return caseId;
    }

    @Override
    public void reject(Long id, CandidateRejectReq req, Long operatorId) {
        CollectCandidateDO candidate = this.getCandidate(id);
        CheckUtils.throwIf(candidate.getStatus() == CandidateStatusEnum.APPROVED || candidate
            .getStatus() == CandidateStatusEnum.MERGED, "候选已生成档案，请先在档案侧处置");
        ValidationUtils.throwIf(req == null || StrUtil.isBlank(req.getReasonCode()), "驳回理由码不能为空");
        String reason = StrUtil.format("{}：{}", req.getReasonCode().trim(), StrUtil.blankToDefault(req
            .getRemark(), "无补充说明"));
        CollectCandidateDO update = new CollectCandidateDO();
        update.setId(id);
        update.setStatus(CandidateStatusEnum.REJECTED);
        update.setRejectReason(StrUtil.subPre(reason, 500));
        update.setUpdateUser(operatorId);
        candidateMapper.updateById(update);
    }

    @Override
    public void delete(List<Long> ids) {
        CheckUtils.throwIf(CollUtil.isEmpty(ids), "请选择要删除的候选");
        List<CollectCandidateDO> list = candidateMapper.selectList(Wrappers.<CollectCandidateDO>lambdaQuery()
            .in(CollectCandidateDO::getId, ids));
        for (CollectCandidateDO candidate : list) {
            CheckUtils.throwIf(candidate.getCaseId() != null && candidate
                .getStatus() != CandidateStatusEnum.DUPLICATED, "候选 [{}] 已生成档案，请走撤回清除以保证档案同步", candidate.getId());
        }
        candidateMapper.deleteByIds(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectEraseResp erase(CollectEraseReq req) {
        ValidationUtils.throwIf(req == null || StrUtil.isBlank(req.getSourceCode()), "采集源标识不能为空");
        ValidationUtils.throwIf(StrUtil.isBlank(req.getRequester()), "请求方不能为空");
        List<String> sourceIds = CollUtil.removeBlank(CollUtil.emptyIfNull(req.getSourceIds()));
        List<String> sourceUrls = CollUtil.removeBlank(CollUtil.emptyIfNull(req.getSourceUrls()));
        CheckUtils.throwIf(CollUtil.isEmpty(sourceIds) && CollUtil.isEmpty(sourceUrls), "来源编号与来源链接至少提供一个，避免整源误删");
        List<CollectCandidateDO> candidates = candidateMapper.selectList(Wrappers.<CollectCandidateDO>lambdaQuery()
            .eq(CollectCandidateDO::getSourceCode, StrUtil.trim(req.getSourceCode()))
            .and(w -> w.in(CollUtil.isNotEmpty(sourceIds), CollectCandidateDO::getSourceId, sourceIds)
                .or(CollUtil.isNotEmpty(sourceUrls))
                .in(CollUtil.isNotEmpty(sourceUrls), CollectCandidateDO::getSourceUrl, sourceUrls)));
        CollectEraseResp resp = new CollectEraseResp();
        resp.setCandidateIds(candidates.stream().map(CollectCandidateDO::getId).toList());
        resp.setCaseIds(candidates.stream()
            .map(CollectCandidateDO::getCaseId)
            .filter(Objects::nonNull)
            .distinct()
            .toList());
        // 候选：转 ERASED 并清除识别字段（保留来源标识与指纹外的追溯信息足够答复站方）
        if (CollUtil.isNotEmpty(candidates)) {
            candidateMapper.lambdaUpdate()
                .set(CollectCandidateDO::getStatus, CandidateStatusEnum.ERASED)
                .set(CollectCandidateDO::getName, null)
                .set(CollectCandidateDO::getAliases, null)
                .set(CollectCandidateDO::getAddress, null)
                .set(CollectCandidateDO::getRegionText, null)
                .set(CollectCandidateDO::getFeatures, null)
                .set(CollectCandidateDO::getNarrative, null)
                .set(CollectCandidateDO::getFamilyClues, null)
                .set(CollectCandidateDO::getOtherNote, null)
                .set(CollectCandidateDO::getContactText, null)
                .set(CollectCandidateDO::getCoverUrl, null)
                .set(CollectCandidateDO::getDedupeKey, null)
                .set(CollectCandidateDO::getPrimaryId, null)
                .in(CollectCandidateDO::getId, resp.getCandidateIds())
                .update();
            // 原文正文一并清空（保留 sha256 / source_url / fetch_time 以证明采过什么）
            rawMapper.lambdaUpdate()
                .set(CollectRawDO::getContent, null)
                .set(CollectRawDO::getErased, Boolean.TRUE)
                .eq(CollectRawDO::getSourceCode, StrUtil.trim(req.getSourceCode()))
                .and(w -> w.in(CollUtil.isNotEmpty(sourceIds), CollectRawDO::getSourceId, sourceIds)
                    .or(CollUtil.isNotEmpty(sourceUrls))
                    .in(CollUtil.isNotEmpty(sourceUrls), CollectRawDO::getSourceUrl, sourceUrls))
                .update();
        }
        for (Long caseId : CollUtil.emptyIfNull(resp.getCaseIds())) {
            this.eraseCase(caseId, req.getRequester());
        }
        resp.setCandidateCount(resp.getCandidateIds().size());
        resp.setCaseCount(resp.getCaseIds().size());
        resp.setResult("已清除候选 %d 条、档案 %d 条".formatted(resp.getCandidateCount(), resp.getCaseCount()));
        CollectEraseLogDO eraseLog = new CollectEraseLogDO();
        eraseLog.setSourceCode(StrUtil.trim(req.getSourceCode()));
        eraseLog.setSourceIds(sourceIds);
        eraseLog.setSourceUrls(sourceUrls);
        eraseLog.setRequester(StrUtil.subPre(req.getRequester().trim(), 128));
        eraseLog.setRemark(StrUtil.subPre(StrUtil.blankToDefault(req.getRemark(), "按来源撤回采集数据"), 500));
        eraseLog.setCandidateIds(resp.getCandidateIds());
        eraseLog.setCaseIds(resp.getCaseIds());
        eraseLog.setResult(StrUtil.subPre(resp.getResult(), 255));
        eraseLogMapper.insert(eraseLog);
        resp.setEraseLogId(eraseLog.getId());
        log.info("采集数据撤回：源 {}，请求方 {}，{}", req.getSourceCode(), req.getRequester(), resp.getResult());
        return resp;
    }

    @Override
    public PageResp<CollectLogResp> pageLog(CollectLogQuery query, PageQuery pageQuery) {
        CollectLogQuery condition = query == null ? new CollectLogQuery() : query;
        LambdaQueryWrapper<CollectLogDO> wrapper = Wrappers.<CollectLogDO>lambdaQuery()
            .eq(StrUtil.isNotBlank(condition.getSourceCode()), CollectLogDO::getSourceCode, StrUtil.trim(condition
                .getSourceCode()))
            .eq(StrUtil.isNotBlank(condition.getBatchNo()), CollectLogDO::getBatchNo, condition.getBatchNo())
            .eq(StrUtil.isNotBlank(condition.getTriggerType()), CollectLogDO::getTriggerType, condition
                .getTriggerType())
            .eq(condition.getSuccess() != null, CollectLogDO::getSuccess, condition.getSuccess())
            .orderByDesc(CollectLogDO::getId);
        Page<CollectLogDO> page = logMapper.selectPage(new Page<>(pageQuery.getPage(), pageQuery.getSize()), wrapper);
        return PageResp.build(page, CollectLogResp.class);
    }

    /**
     * 档案侧撤回：转 ERASED 并清除识别字段（与档案域删除权口径一致）
     */
    private void eraseCase(Long caseId, String requester) {
        FindCaseDO caseDO = findCaseMapper.selectById(caseId);
        if (caseDO == null || caseDO.getStatus() == CaseStatusEnum.ERASED) {
            return;
        }
        findCaseMapper.lambdaUpdate()
            .set(FindCaseDO::getStatus, CaseStatusEnum.ERASED)
            .set(FindCaseDO::getName, CaseConstants.ERASED_NAME)
            .set(FindCaseDO::getAddress, null)
            .set(FindCaseDO::getFeatures, null)
            .set(FindCaseDO::getNarrative, null)
            .set(FindCaseDO::getFamilyClues, null)
            .set(FindCaseDO::getOtherNote, null)
            .set(FindCaseDO::getClothing, null)
            .set(FindCaseDO::getBelongings, null)
            .set(FindCaseDO::getAccent, null)
            .set(FindCaseDO::getContactPhone, null)
            .set(FindCaseDO::getDedupeKey, null)
            .set(FindCaseDO::getSourceUrl, null)
            .set(FindCaseDO::getArchivedTime, null)
            .set(FindCaseDO::getUpdateUser, null)
            .eq(FindCaseDO::getId, caseId)
            .update();
        this.saveStatusLog(caseId, caseDO
            .getStatus(), CaseStatusEnum.ERASED, REASON_CODE_ERASE, "来源撤回请求，采集数据一并清除（请求方：%s）".formatted(StrUtil
                .subPre(requester, 64)), null);
    }

    /**
     * 由候选构造对外档案（来源字段随行落库，便于追溯与批量撤回）
     */
    private FindCaseDO buildCase(CollectCandidateDO candidate, Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        FindCaseDO caseDO = new FindCaseDO();
        caseDO.setId(IdUtil.getSnowflakeNextId());
        caseDO.setCaseNo(CaseConstants.CASE_NO_PREFIX + DateUtil.format(now, "yyMMdd") + StringConstants.DASHED + Long
            .toString(caseDO.getId(), 36)
            .toUpperCase());
        caseDO.setStatus(CaseStatusEnum.PUBLISHED);
        caseDO.setUrgencyLevel(UrgencyLevelEnum.evaluate(candidate.getMissingTime(), now));
        caseDO.setName(candidate.getName());
        caseDO.setGender(candidate.getGender() == null ? GenderEnum.UNKNOWN : candidate.getGender());
        caseDO.setBirthday(candidate.getBirthday());
        caseDO.setAgeAtMissing(candidate.getAgeAtMissing());
        AgeGroupEnum ageGroup = AgeGroupEnum.of(candidate.getAgeAtMissing());
        if (ageGroup != null) {
            caseDO.setAgeGroup(ageGroup.getValue());
        }
        caseDO.setMissingTime(candidate.getMissingTime());
        caseDO.setProvinceCode(candidate.getProvinceCode());
        caseDO.setCityCode(candidate.getCityCode());
        caseDO.setDistrictCode(candidate.getDistrictCode());
        caseDO.setAddress(candidate.getAddress());
        caseDO.setHeight(candidate.getHeight());
        caseDO.setFeatures(candidate.getFeatures());
        caseDO.setCaseType(candidate.getCaseType());
        caseDO.setBloodSampleStatus(StrUtil.blankToDefault(candidate.getBloodSampleStatus(), "UNKNOWN"));
        caseDO.setNarrative(candidate.getNarrative());
        caseDO.setFamilyClues(candidate.getFamilyClues());
        caseDO.setOtherNote(candidate.getOtherNote());
        caseDO.setContactMode(CaseConstants.CONTACT_MODE_IN_APP);
        caseDO.setVersion(1);
        caseDO.setPublishTime(now);
        caseDO.setLastConfirmTime(now);
        caseDO.setSourceType(CollectConstants.SOURCE_TYPE_COLLECT);
        caseDO.setSourceCode(candidate.getSourceCode());
        caseDO.setSourceId(candidate.getSourceId());
        caseDO.setSourceUrl(candidate.getSourceUrl());
        caseDO.setFetchTime(candidate.getFetchTime());
        caseDO.setDedupeKey(candidate.getDedupeKey());
        caseDO.setCreateUser(operatorId);
        caseDO.setUpdateUser(operatorId);
        caseDO.setAuditRemark("采集来源数据，已经人工审核");
        return caseDO;
    }

    private FindCaseDO findCaseBySource(CollectCandidateDO candidate) {
        return findCaseMapper.selectOne(Wrappers.<FindCaseDO>lambdaQuery()
            .eq(FindCaseDO::getSourceType, CollectConstants.SOURCE_TYPE_COLLECT)
            .eq(FindCaseDO::getSourceCode, candidate.getSourceCode())
            .eq(FindCaseDO::getSourceId, candidate.getSourceId())
            .last("LIMIT 1"));
    }

    /**
     * 站内同指纹档案（排除本源同编号，避免把"自己的档案"当成疑似重复）
     */
    private List<Long> listSimilarCaseIds(CollectCandidateDO candidate) {
        if (StrUtil.isBlank(candidate.getDedupeKey())) {
            return List.of();
        }
        return findCaseMapper.selectList(Wrappers.<FindCaseDO>lambdaQuery()
            .select(FindCaseDO::getId)
            .eq(FindCaseDO::getDedupeKey, candidate.getDedupeKey())
            .ne(FindCaseDO::getSourceType, CollectConstants.SOURCE_TYPE_COLLECT)
            .in(FindCaseDO::getStatus, CaseStatusEnum.visiblePublicly())).stream().map(FindCaseDO::getId).toList();
    }

    private void markApproved(CollectCandidateDO candidate, Long caseId, Long operatorId) {
        CollectCandidateDO update = new CollectCandidateDO();
        update.setId(candidate.getId());
        update.setStatus(CandidateStatusEnum.APPROVED);
        update.setCaseId(caseId);
        update.setUpdateUser(operatorId);
        candidateMapper.updateById(update);
    }

    private void saveSourceLink(CollectCandidateDO candidate, Long caseId, String linkType, Long operatorId) {
        CaseSourceLinkDO link = new CaseSourceLinkDO();
        link.setCaseId(caseId);
        link.setCandidateId(candidate.getId());
        link.setSourceCode(candidate.getSourceCode());
        link.setSourceId(candidate.getSourceId());
        link.setSourceUrl(candidate.getSourceUrl());
        link.setFetchTime(candidate.getFetchTime());
        link.setLinkType(linkType);
        link.setCreateUser(operatorId);
        link.setUpdateUser(operatorId);
        try {
            caseSourceLinkMapper.insert(link);
        } catch (DuplicateKeyException e) {
            log.warn("来源关联已存在（候选 {}，档案 {}），按幂等处理", candidate.getId(), caseId);
        }
    }

    private void saveStatusLog(Long caseId,
                               CaseStatusEnum from,
                               CaseStatusEnum to,
                               String reasonCode,
                               String reason,
                               Long operatorId) {
        CaseStatusLogDO statusLog = new CaseStatusLogDO();
        statusLog.setCaseId(caseId);
        statusLog.setFromStatus(from);
        statusLog.setToStatus(to);
        statusLog.setReasonCode(reasonCode);
        statusLog.setReason(StrUtil.subPre(reason, 500));
        statusLog.setOperatorId(operatorId);
        statusLog.setOperatorType(CaseOperatorTypeEnum.SYS.name());
        statusLog.setCreateUser(operatorId);
        statusLog.setUpdateUser(operatorId);
        caseStatusLogMapper.insert(statusLog);
    }

    /**
     * 并发保护：同一候选同一时刻只允许一个审批在跑（Redis 不可用时降级，最终仍由来源唯一键保证不重复）
     */
    private void guardConcurrentApproval(Long candidateId) {
        boolean acquired;
        try {
            acquired = RedisUtils.setIfAbsent(CollectConstants.APPROVE_LOCK_KEY_PREFIX + candidateId, String
                .valueOf(candidateId), CollectConstants.APPROVE_LOCK_TTL);
        } catch (Exception e) {
            log.warn("审批并发保护键不可用，降级依赖来源唯一键：{}", e.getMessage());
            acquired = true;
        }
        CheckUtils.throwIf(!acquired, "该候选正在审批中，请稍后重试");
    }

    private CollectCandidateDO getCandidate(Long id) {
        CheckUtils.throwIfNull(id, "候选 ID 不能为空");
        CollectCandidateDO candidate = candidateMapper.selectById(id);
        CheckUtils.throwIfNull(candidate, "采集候选不存在或已删除");
        return candidate;
    }

    private CandidateResp toResp(CollectCandidateDO candidate) {
        CandidateResp resp = BeanUtil.copyProperties(candidate, CandidateResp.class);
        resp.setCreateTime(candidate.getCreateTime());
        return resp;
    }
}
