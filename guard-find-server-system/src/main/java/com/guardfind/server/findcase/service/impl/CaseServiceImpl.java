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

package com.guardfind.server.findcase.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.DesensitizedUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.common.enums.GenderEnum;
import com.guardfind.server.findcase.enums.AgeGroupEnum;
import com.guardfind.server.findcase.enums.CaseOperatorTypeEnum;
import com.guardfind.server.findcase.enums.CaseStatusEnum;
import com.guardfind.server.findcase.enums.TrackEventEnum;
import com.guardfind.server.findcase.enums.UrgencyLevelEnum;
import com.guardfind.server.findcase.mapper.CaseVersionMapper;
import com.guardfind.server.findcase.mapper.FindCaseAliasMapper;
import com.guardfind.server.findcase.mapper.FindCaseMapper;
import com.guardfind.server.findcase.mapper.FindCaseMediaMapper;
import com.guardfind.server.findcase.mapper.UserTrackMapper;
import com.guardfind.server.findcase.model.entity.CaseVersionDO;
import com.guardfind.server.findcase.model.entity.FindCaseAliasDO;
import com.guardfind.server.findcase.model.entity.FindCaseDO;
import com.guardfind.server.findcase.model.entity.FindCaseMediaDO;
import com.guardfind.server.findcase.model.entity.UserTrackDO;
import com.guardfind.server.findcase.model.query.CaseMineQuery;
import com.guardfind.server.findcase.model.req.CaseActionReq;
import com.guardfind.server.findcase.model.req.CaseAliasReq;
import com.guardfind.server.findcase.model.req.CaseFoundReq;
import com.guardfind.server.findcase.model.req.CaseMediaReq;
import com.guardfind.server.findcase.model.req.CaseReq;
import com.guardfind.server.findcase.model.req.CaseSubmitReq;
import com.guardfind.server.findcase.model.resp.CaseDetailResp;
import com.guardfind.server.findcase.model.resp.CaseMinePageResp;
import com.guardfind.server.findcase.model.resp.CasePublicResp;
import com.guardfind.server.findcase.model.resp.CaseStatusSummaryResp;
import com.guardfind.server.findcase.service.CaseService;
import com.guardfind.server.findcase.service.CaseStatusService;
import com.guardfind.server.findcase.service.ContentCheckGateway;
import com.guardfind.server.findcase.support.CaseConstants;
import com.guardfind.server.findcase.support.CaseSubmitValidator;
import com.guardfind.server.findcase.support.CaseViewAssembler;
import com.guardfind.server.findcase.util.CaseDedupeKeyUtil;
import com.guardfind.server.notification.enums.NotificationEventEnum;
import com.guardfind.server.notification.service.AppMessageService;
import com.guardfind.server.system.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.starter.cache.redisson.util.RedisUtils;
import top.continew.starter.core.constant.StringConstants;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.core.util.ServletUtils;
import top.continew.starter.core.util.validation.CheckUtils;
import top.continew.starter.core.util.validation.ValidationUtils;
import top.continew.starter.extension.crud.model.query.PageQuery;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 寻人档案业务实现
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseServiceImpl implements CaseService {

    /**
     * 找回确认依据白名单
     */
    private static final Set<String> FOUND_CONFIRM_TYPES = Set.of("POLICE", "GUARDIAN", "DNA");

    /**
     * 疑似重复检测窗口（天）
     */
    private static final int DUPLICATE_DAY_WINDOW = 7;

    /**
     * 临期提醒去重键前缀（一个确认周期只提醒一次）
     */
    private static final String EXPIRE_NOTICE_KEY_PREFIX = "CASE:EXPIRE_NOTICE:";

    private final FindCaseMapper findCaseMapper;
    private final FindCaseMediaMapper findCaseMediaMapper;
    private final FindCaseAliasMapper findCaseAliasMapper;
    private final CaseVersionMapper caseVersionMapper;
    private final UserTrackMapper userTrackMapper;
    private final FileService fileService;
    private final CaseViewAssembler caseViewAssembler;
    private final ContentCheckGateway contentCheckGateway;
    private final CaseStatusService caseStatusService;
    private final AppMessageService appMessageService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CaseDetailResp create(CaseReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        LocalDateTime now = LocalDateTime.now();
        FindCaseDO caseDO = new FindCaseDO();
        caseDO.setOwnerId(userId);
        caseDO.setStatus(CaseStatusEnum.DRAFT);
        caseDO.setSourceType(CaseConstants.SOURCE_TYPE_APP);
        caseDO.setVersion(0);
        this.applyReq(caseDO, req, EditScope.FULL);
        List<FindCaseMediaDO> mediaList = this.buildMediaList(req.getMediaUrls(), null, userId);
        // 建档即分配对外编号，采集来源同样复用该规则
        caseDO.setId(IdUtil.getSnowflakeNextId());
        caseDO.setCaseNo(this.buildCaseNo(caseDO.getId(), now));
        caseDO.setDedupeKey(this.buildDedupeKey(caseDO));
        caseDO.setUrgencyLevel(UrgencyLevelEnum.evaluate(caseDO.getMissingTime(), now));
        caseDO.setCreateUser(userId);
        caseDO.setUpdateUser(userId);
        boolean submit = Boolean.TRUE.equals(req.getSubmit());
        if (submit) {
            // 直接提交：先校验后落库，避免校验失败留下"半条已提交档案"
            this.checkContentSecurity(caseDO);
            CaseSubmitValidator.validate(caseDO, mediaList, this.countTodayCases(userId, now));
            this.checkDuplicate(caseDO, Boolean.TRUE.equals(req.getForceSubmit()));
        }
        findCaseMapper.insert(caseDO);
        this.saveMedia(caseDO.getId(), mediaList, userId);
        this.saveAliases(caseDO.getId(), req.getAliases(), userId);
        if (submit) {
            this.doSubmit(caseDO, userId, now);
        }
        return caseViewAssembler.toDetail(caseDO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CaseDetailResp update(CaseReq req, Long id) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        LocalDateTime now = LocalDateTime.now();
        FindCaseDO caseDO = this.getOwnedCase(id, userId);
        EditScope scope = this.resolveEditScope(caseDO.getStatus());
        CheckUtils.throwIf(scope == EditScope.NONE, "档案当前为 [{}]，不允许修改", this.statusDescription(caseDO.getStatus()));
        if (scope == EditScope.SUPPLEMENT) {
            CheckUtils.throwIf(!this.isCoreInfoUnchanged(caseDO, req), "已发布档案仅可补充体貌、经过与联络信息；如需修改姓名、性别、失踪时间或失踪地，请先撤回发布");
        }
        this.checkContentSecurity(this.buildContentPreview(caseDO, req, scope));
        this.applyReq(caseDO, req, scope);
        caseDO.setDedupeKey(this.buildDedupeKey(caseDO));
        caseDO.setUrgencyLevel(UrgencyLevelEnum.evaluate(caseDO.getMissingTime(), now));
        caseDO.setUpdateUser(userId);
        caseDO.setUpdateTime(now);
        findCaseMapper.updateById(caseDO);
        this.replaceRelations(caseDO.getId(), req.getMediaUrls(), req.getAliases(), userId);
        return caseViewAssembler.toDetail(caseDO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CaseDetailResp submit(Long id, CaseSubmitReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        LocalDateTime now = LocalDateTime.now();
        FindCaseDO caseDO = this.getOwnedCase(id, userId);
        CheckUtils.throwIf(!CaseStatusEnum.canTransfer(caseDO
            .getStatus(), CaseStatusEnum.PENDING_REVIEW), "状态冲突：档案当前为 [{}]，不能提交审核", this.statusDescription(caseDO
                .getStatus()));
        this.checkContentSecurity(caseDO);
        CaseSubmitValidator.validate(caseDO, this.listMedia(caseDO.getId()), this.countTodayCases(userId, now));
        this.checkDuplicate(caseDO, req != null && Boolean.TRUE.equals(req.getForceSubmit()));
        this.doSubmit(caseDO, userId, now);
        return caseViewAssembler.toDetail(caseDO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void found(Long id, CaseFoundReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        FindCaseDO caseDO = this.getOwnedCase(id, userId);
        ValidationUtils.throwIf(StrUtil.isBlank(req.getConfirmType()), "找回确认依据不能为空");
        String confirmType = req.getConfirmType().trim().toUpperCase();
        ValidationUtils.throwIf(!FOUND_CONFIRM_TYPES.contains(confirmType), "找回确认依据仅支持 POLICE/GUARDIAN/DNA");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime foundTime = req.getFoundTime() == null ? now : req.getFoundTime();
        ValidationUtils.throwIf(foundTime.isAfter(now), "找回时间不能晚于当前时间");
        ValidationUtils.throwIf(caseDO.getMissingTime() != null && foundTime.isBefore(caseDO
            .getMissingTime()), "找回时间不能早于失踪时间");
        caseDO.setFoundConfirmType(confirmType);
        caseDO.setFoundRemark(req.getRemark());
        caseDO.setFoundTime(foundTime);
        caseDO.setUpdateUser(userId);
        caseDO.setUpdateTime(now);
        findCaseMapper.updateById(caseDO);
        caseStatusService.change(caseDO, CaseStatusEnum.FOUND, null, "发布人确认找回（依据：%s）"
            .formatted(confirmType), userId, CaseOperatorTypeEnum.APP);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renew(Long id, CaseActionReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        LocalDateTime now = LocalDateTime.now();
        FindCaseDO caseDO = this.getOwnedCase(id, userId);
        CaseStatusEnum status = caseDO.getStatus();
        CheckUtils
            .throwIf(status != CaseStatusEnum.PUBLISHED && status != CaseStatusEnum.ARCHIVED, "状态冲突：档案当前为 [{}]，无需续期", this
                .statusDescription(status));
        String reason = StrUtil.blankToDefault(req == null ? null : req.getReason(), "发布人续期");
        if (status == CaseStatusEnum.ARCHIVED) {
            // 归档后续期等同重新发布，仍走同一状态漏斗留痕
            caseStatusService
                .change(caseDO, CaseStatusEnum.PUBLISHED, "RENEW", reason, userId, CaseOperatorTypeEnum.APP);
            return;
        }
        FindCaseDO update = new FindCaseDO();
        update.setId(caseDO.getId());
        update.setLastConfirmTime(now);
        update.setUpdateUser(userId);
        update.setUpdateTime(now);
        findCaseMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long id, CaseActionReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        FindCaseDO caseDO = this.getOwnedCase(id, userId);
        String reason = StrUtil.blankToDefault(req == null ? null : req.getReason(), "发布人主动撤回");
        caseStatusService
            .change(caseDO, CaseStatusEnum.WITHDRAWN, "OWNER_WITHDRAW", reason, userId, CaseOperatorTypeEnum.APP);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void erase(Long id, CaseActionReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        FindCaseDO caseDO = findCaseMapper.selectById(id);
        CheckUtils.throwIfNull(caseDO, "档案不存在或已删除");
        CheckUtils.throwIf(caseDO.getStatus() == CaseStatusEnum.ERASED, "档案已删除，无需重复操作");
        CheckUtils.throwIf(!this.canErase(caseDO, userId), "无权删除该档案");
        String reason = StrUtil.blankToDefault(req == null ? null : req.getReason(), "发布人行使删除权");
        caseStatusService
            .change(caseDO, CaseStatusEnum.ERASED, "ERASE_REQUEST", reason, userId, CaseOperatorTypeEnum.APP);
        // 状态先落库再清除识别字段：即使清除失败也不会留下"对外可见但无姓名"的中间态
        List<Long> fileIds = this.clearIdentifiableInfo(caseDO, userId);
        if (CollUtil.isNotEmpty(fileIds)) {
            // 媒体回收：交由文件服务的回收站管道彻底清除，不留孤儿图片
            fileService.delete(fileIds);
        }
    }

    @Override
    public CasePublicResp getPublic(Long id) {
        FindCaseDO caseDO = findCaseMapper.selectById(id);
        if (caseDO == null || !this.isVisiblePublicly(caseDO.getStatus())) {
            // 草稿、审核中、驳回、撤回、归档、下架、删除统一表现为"不存在"，避免状态探测
            throw new BusinessException("该信息不存在或已下架");
        }
        this.increaseViewCount(id);
        return caseViewAssembler.toPublic(caseDO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CaseDetailResp getFull(Long id) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        FindCaseDO caseDO = findCaseMapper.selectById(id);
        if (caseDO == null || caseDO.getStatus() == CaseStatusEnum.ERASED) {
            throw new BusinessException("该信息不存在或已下架");
        }
        CheckUtils.throwIf(!this.canViewFull(caseDO, userId), "无权限查看该档案的联系方式与详细地址");
        this.trackViewFull(id, userId);
        return caseViewAssembler.toDetail(caseDO);
    }

    @Override
    public CaseMinePageResp mine(CaseMineQuery query, PageQuery pageQuery) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        LambdaQueryWrapper<FindCaseDO> wrapper = Wrappers.<FindCaseDO>lambdaQuery()
            .eq(FindCaseDO::getOwnerId, userId)
            .eq(query.getStatus() != null, FindCaseDO::getStatus, query.getStatus())
            .and(StrUtil.isNotBlank(query.getKeyword()), w -> w.like(FindCaseDO::getName, query.getKeyword())
                .or()
                .like(FindCaseDO::getFeatures, query.getKeyword()))
            .orderByDesc(FindCaseDO::getCreateTime)
            .orderByDesc(FindCaseDO::getId);
        Page<FindCaseDO> result = findCaseMapper.selectPage(new Page<>(pageQuery.getPage(), pageQuery
            .getSize()), wrapper);
        return CaseMinePageResp.of(caseViewAssembler.toDetailList(result.getRecords()), result.getTotal(), this
            .summarize(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int runExpiryTask() {
        LocalDateTime now = LocalDateTime.now();
        String confirmExpr = "COALESCE(last_confirm_time, publish_time, create_time)";
        // 1.超过 90 天无人确认 → 自动归档（走状态漏斗，留痕 + 通知）
        List<FindCaseDO> expiredList = findCaseMapper.lambdaQuery()
            .eq(FindCaseDO::getStatus, CaseStatusEnum.PUBLISHED)
            .apply(confirmExpr + " <= {0}", now.minusDays(CaseConstants.EXPIRY_DAYS))
            .list();
        for (FindCaseDO caseDO : expiredList) {
            caseStatusService.change(caseDO, CaseStatusEnum.ARCHIVED, "EXPIRED", "超过 %d 天无人确认，系统自动归档"
                .formatted(CaseConstants.EXPIRY_DAYS), CaseConstants.SYSTEM_OPERATOR_ID, CaseOperatorTypeEnum.SYSTEM);
        }
        // 2.归档前 7 天提醒（同一确认周期只提醒一次）
        List<FindCaseDO> expiringList = findCaseMapper.lambdaQuery()
            .eq(FindCaseDO::getStatus, CaseStatusEnum.PUBLISHED)
            .apply(confirmExpr + " > {0}", now.minusDays(CaseConstants.EXPIRY_DAYS))
            .apply(confirmExpr + " <= {0}", now
                .minusDays(CaseConstants.EXPIRY_DAYS - CaseConstants.EXPIRY_NOTICE_ADVANCE_DAYS))
            .list();
        for (FindCaseDO caseDO : expiringList) {
            if (!RedisUtils.setIfAbsent(EXPIRE_NOTICE_KEY_PREFIX + caseDO.getId(), now.toString(), Duration
                .ofDays(CaseConstants.EXPIRY_DAYS))) {
                continue;
            }
            appMessageService.send(caseDO
                .getOwnerId(), NotificationEventEnum.CASE_EXPIRING, CaseConstants.MESSAGE_BIZ_TYPE, caseDO
                    .getId(), "档案即将归档", "您发布的寻人档案「%s」将在 %d 天后到期归档，请在「我的发布」中续期。".formatted(caseDO
                        .getName(), CaseConstants.EXPIRY_NOTICE_ADVANCE_DAYS));
        }
        return expiredList.size();
    }

    /**
     * 提交审核的落库动作（前置校验由调用方完成）
     */
    private void doSubmit(FindCaseDO caseDO, Long userId, LocalDateTime now) {
        int version = Objects.requireNonNullElse(caseDO.getVersion(), 0) + 1;
        caseDO.setVersion(version);
        caseDO.setUrgencyLevel(UrgencyLevelEnum.evaluate(caseDO.getMissingTime(), now));
        caseDO.setDedupeKey(this.buildDedupeKey(caseDO));
        caseDO.setUpdateUser(userId);
        caseDO.setUpdateTime(now);
        findCaseMapper.updateById(caseDO);
        caseStatusService
            .change(caseDO, CaseStatusEnum.PENDING_REVIEW, null, "发布人提交审核", userId, CaseOperatorTypeEnum.APP);
        this.saveVersionSnapshot(caseDO, version, userId, now);
    }

    /**
     * 写全量版本快照（审核人据此比对"改了哪一版"）
     */
    private void saveVersionSnapshot(FindCaseDO caseDO, int version, Long userId, LocalDateTime now) {
        FindCaseDO snapshot = BeanUtil.copyProperties(caseDO, FindCaseDO.class);
        // 快照仅用于差异比对，联系方式打码后落库
        snapshot.setContactPhone(DesensitizedUtil.mobilePhone(caseDO.getContactPhone()));
        CaseVersionDO versionDO = new CaseVersionDO();
        versionDO.setCaseId(caseDO.getId());
        versionDO.setSeq(version);
        versionDO.setSnapshotJson(JSONUtil.toJsonStr(snapshot));
        versionDO.setReason("第 " + version + " 次提交审核");
        versionDO.setOperatorId(userId);
        versionDO.setOperatorType(CaseOperatorTypeEnum.APP.name());
        versionDO.setCreateUser(userId);
        versionDO.setCreateTime(now);
        versionDO.setUpdateUser(userId);
        versionDO.setUpdateTime(now);
        caseVersionMapper.insert(versionDO);
    }

    /**
     * 疑似重复检测：规范化姓名 + 性别 + 失踪日期 ±7 天 + 区划到市，命中即要求显式 forceSubmit
     */
    private void checkDuplicate(FindCaseDO caseDO, boolean forceSubmit) {
        if (forceSubmit || caseDO.getMissingTime() == null || StrUtil.isBlank(caseDO.getName())) {
            return;
        }
        List<FindCaseDO> hits = findCaseMapper.lambdaQuery()
            .in(FindCaseDO::getDedupeKey, this.dedupeKeysInWindow(caseDO))
            .in(FindCaseDO::getStatus, this.publiclyVisibleStatuses())
            .ne(caseDO.getId() != null, FindCaseDO::getId, caseDO.getId())
            .orderByDesc(FindCaseDO::getCreateTime)
            .last("LIMIT 3")
            .list();
        if (CollUtil.isEmpty(hits)) {
            return;
        }
        String caseNos = hits.stream()
            .map(FindCaseDO::getCaseNo)
            .filter(StrUtil::isNotBlank)
            .collect(Collectors.joining("、"));
        throw new BusinessException("存在疑似重复档案（编号：%s），请核对是否为同一人；确认无误后可重新提交并携带 forceSubmit=true，或联系志愿者走合并流程"
            .formatted(caseNos));
    }

    private List<String> dedupeKeysInWindow(FindCaseDO caseDO) {
        List<String> keys = new ArrayList<>(DUPLICATE_DAY_WINDOW * 2 + 1);
        for (int offset = -DUPLICATE_DAY_WINDOW; offset <= DUPLICATE_DAY_WINDOW; offset++) {
            keys.add(this.buildDedupeKey(caseDO, caseDO.getMissingTime().plusDays(offset)));
        }
        return keys;
    }

    private String buildDedupeKey(FindCaseDO caseDO) {
        return this.buildDedupeKey(caseDO, caseDO.getMissingTime());
    }

    private String buildDedupeKey(FindCaseDO caseDO, LocalDateTime missingTime) {
        Integer genderValue = caseDO.getGender() == null ? null : caseDO.getGender().getValue();
        return CaseDedupeKeyUtil.build(caseDO.getName(), genderValue, missingTime, caseDO.getProvinceCode(), caseDO
            .getCityCode());
    }

    private String buildCaseNo(Long id, LocalDateTime now) {
        return CaseConstants.CASE_NO_PREFIX + DateUtil.format(now, "yyMMdd") + StringConstants.DASHED + Long
            .toString(id, 36)
            .toUpperCase();
    }

    /**
     * 内容安全检查（欺诈词、违法词由治理域匹配器给出结论）
     */
    private void checkContentSecurity(FindCaseDO caseDO) {
        String reason = contentCheckGateway.check(this.buildContentText(caseDO));
        ValidationUtils.throwIf(StrUtil.isNotBlank(reason), "内容不合规：%s", reason);
    }

    private FindCaseDO buildContentPreview(FindCaseDO caseDO, CaseReq req, EditScope scope) {
        FindCaseDO preview = BeanUtil.copyProperties(caseDO, FindCaseDO.class);
        this.applyReq(preview, req, scope);
        return preview;
    }

    private String buildContentText(FindCaseDO caseDO) {
        return String.join(StringConstants.SPACE, StrUtil.emptyIfNull(caseDO.getName()), StrUtil.emptyIfNull(caseDO
            .getFeatures()), StrUtil.emptyIfNull(caseDO.getNarrative()), StrUtil.emptyIfNull(caseDO
                .getFamilyClues()), StrUtil.emptyIfNull(caseDO.getOtherNote()), StrUtil.emptyIfNull(caseDO
                    .getClothing()), StrUtil.emptyIfNull(caseDO.getBelongings()));
    }

    /**
     * 请求参数映射到实体
     *
     * <p>
     * SUPPLEMENT 语义为"补充"：请求未传的补充字段保留原值，核心身份信息一律不覆盖。
     * </p>
     *
     * @param caseDO 实体
     * @param req    请求参数
     * @param scope  可编辑范围
     */
    private void applyReq(FindCaseDO caseDO, CaseReq req, EditScope scope) {
        if (scope == EditScope.FULL) {
            caseDO.setName(StrUtil.trim(req.getName()));
            caseDO.setGender(this.parseGender(req.getGender()));
            caseDO.setBirthday(req.getBirthday());
            caseDO.setAgeAtMissing(req.getAgeAtMissing());
            caseDO.setRelationType(req.getRelationType());
            caseDO.setMissingTime(req.getMissingTime());
            caseDO.setProvinceCode(StrUtil.trimToNull(req.getProvinceCode()));
            caseDO.setCityCode(StrUtil.trimToNull(req.getCityCode()));
            caseDO.setDistrictCode(StrUtil.trimToNull(req.getDistrictCode()));
            caseDO.setCaseType(StrUtil.blankToDefault(req.getCaseType(), caseDO.getCaseType()));
            AgeGroupEnum ageGroup = AgeGroupEnum.of(req.getAgeAtMissing());
            if (ageGroup != null) {
                caseDO.setAgeGroup(ageGroup.getValue());
            }
        }
        caseDO.setAddress(this.patch(scope, caseDO.getAddress(), req.getAddress()));
        caseDO.setHometownCode(this.patch(scope, caseDO.getHometownCode(), req.getHometownCode()));
        caseDO.setLng(this.patch(scope, caseDO.getLng(), req.getLng()));
        caseDO.setLat(this.patch(scope, caseDO.getLat(), req.getLat()));
        caseDO.setHeight(this.patch(scope, caseDO.getHeight(), req.getHeight()));
        caseDO.setBodyType(this.patch(scope, caseDO.getBodyType(), req.getBodyType()));
        caseDO.setHairType(this.patch(scope, caseDO.getHairType(), req.getHairType()));
        caseDO.setAccent(this.patch(scope, caseDO.getAccent(), req.getAccent()));
        caseDO.setFeatures(this.patch(scope, caseDO.getFeatures(), req.getFeatures()));
        caseDO.setClothing(this.patch(scope, caseDO.getClothing(), req.getClothing()));
        caseDO.setBelongings(this.patch(scope, caseDO.getBelongings(), req.getBelongings()));
        caseDO.setNarrative(this.patch(scope, caseDO.getNarrative(), req.getNarrative()));
        caseDO.setFamilyClues(this.patch(scope, caseDO.getFamilyClues(), req.getFamilyClues()));
        caseDO.setOtherNote(this.patch(scope, caseDO.getOtherNote(), req.getOtherNote()));
        caseDO.setPoliceReported(this.patch(scope, caseDO.getPoliceReported(), req.getPoliceReported()));
        caseDO.setPoliceStation(this.patch(scope, caseDO.getPoliceStation(), req.getPoliceStation()));
        // 契约中的 caseNo 是"警方立案编号"，档案对外编号由服务端生成，两者不可混用
        caseDO.setCaseAcceptedNo(this.patch(scope, caseDO.getCaseAcceptedNo(), req.getCaseNo()));
        caseDO.setBloodSampleStatus(this.patch(scope, caseDO.getBloodSampleStatus(), req.getBloodSampleStatus()));
        caseDO.setFollowVolunteer(this.patch(scope, caseDO.getFollowVolunteer(), req.getFollowVolunteer()));
        caseDO.setContactMode(StrUtil.blankToDefault(req.getContactMode(), StrUtil.blankToDefault(caseDO
            .getContactMode(), CaseConstants.CONTACT_MODE_IN_APP)));
        caseDO.setContactPhone(StrUtil.isBlank(req.getContactPhone())
            ? caseDO.getContactPhone()
            : req.getContactPhone());
        caseDO.setAgreementVersion(StrUtil.blankToDefault(req.getAgreementVersion(), caseDO.getAgreementVersion()));
        caseDO.setAgreementAccepted(this.patch(scope, caseDO.getAgreementAccepted(), req.getAgreementAccepted()));
        if (Boolean.TRUE.equals(caseDO.getAgreementAccepted()) && caseDO.getAgreementTime() == null) {
            caseDO.setAgreementTime(LocalDateTime.now());
        }
        ValidationUtils.throwIf(CaseConstants.CONTACT_MODE_PHONE.equals(caseDO.getContactMode()) && StrUtil
            .isBlank(caseDO.getContactPhone()), "联络方式为 PHONE 时，联系电话不能为空");
    }

    /**
     * 补充模式下请求未传值的字段保留原值
     */
    private <T> T patch(EditScope scope, T current, T incoming) {
        return scope == EditScope.SUPPLEMENT && incoming == null ? current : incoming;
    }

    private GenderEnum parseGender(String gender) {
        if (StrUtil.isBlank(gender)) {
            return GenderEnum.UNKNOWN;
        }
        try {
            return GenderEnum.valueOf(gender.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            ValidationUtils.throwIf(true, "性别取值无效");
            return GenderEnum.UNKNOWN;
        }
    }

    private EditScope resolveEditScope(CaseStatusEnum status) {
        if (status == null) {
            return EditScope.NONE;
        }
        return switch (status) {
            case DRAFT, REJECTED, WITHDRAWN -> EditScope.FULL;
            case PUBLISHED, ARCHIVED -> EditScope.SUPPLEMENT;
            default -> EditScope.NONE;
        };
    }

    /**
     * 核心身份信息是否未被改动（已发布档案只允许"补充"）
     */
    private boolean isCoreInfoUnchanged(FindCaseDO caseDO, CaseReq req) {
        if (req == null) {
            return true;
        }
        return this.equalsIgnoreCase(StrUtil.trimToNull(req.getName()), StrUtil.trimToNull(caseDO.getName())) && this
            .isGenderUnchanged(caseDO.getGender(), req.getGender()) && Objects.equals(req.getBirthday(), caseDO
                .getBirthday()) && Objects.equals(req.getAgeAtMissing(), caseDO.getAgeAtMissing()) && Objects.equals(req
                    .getMissingTime(), caseDO.getMissingTime()) && this.equalsIgnoreCase(StrUtil.trimToNull(req
                        .getProvinceCode()), StrUtil.trimToNull(caseDO.getProvinceCode())) && this
                            .equalsIgnoreCase(StrUtil.trimToNull(req.getCityCode()), StrUtil.trimToNull(caseDO
                                .getCityCode())) && this.equalsIgnoreCase(StrUtil.trimToNull(req
                                    .getDistrictCode()), StrUtil.trimToNull(caseDO.getDistrictCode()));
    }

    private boolean isGenderUnchanged(GenderEnum current, String incoming) {
        if (StrUtil.isBlank(incoming)) {
            return current == null || current == GenderEnum.UNKNOWN;
        }
        return current != null && this.equalsIgnoreCase(current.name(), incoming);
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left == null ? right == null : left.equalsIgnoreCase(right);
    }

    private String statusDescription(CaseStatusEnum status) {
        return status == null ? "未知" : status.getDescription();
    }

    private Set<CaseStatusEnum> publiclyVisibleStatuses() {
        Set<CaseStatusEnum> statuses = EnumSet.noneOf(CaseStatusEnum.class);
        statuses.addAll(CaseStatusEnum.visiblePublicly());
        statuses.addAll(CaseStatusEnum.foundVisible());
        return statuses;
    }

    private boolean isVisiblePublicly(CaseStatusEnum status) {
        return status != null && this.publiclyVisibleStatuses().contains(status);
    }

    private FindCaseDO getOwnedCase(Long id, Long userId) {
        FindCaseDO caseDO = findCaseMapper.selectById(id);
        CheckUtils.throwIfNull(caseDO, "档案不存在或已删除");
        CheckUtils.throwIf(!Objects.equals(caseDO.getOwnerId(), userId), "无权操作他人档案");
        return caseDO;
    }

    /**
     * 完整视图可见性：本人 / 志愿者 / 审核账号
     */
    private boolean canViewFull(FindCaseDO caseDO, Long userId) {
        return Objects.equals(caseDO.getOwnerId(), userId) || AppUserContextHolder.isVolunteer() || this.isStaff();
    }

    /**
     * 删除权处置范围：本人 / 审核账号（志愿者不得删除他人档案）
     */
    private boolean canErase(FindCaseDO caseDO, Long userId) {
        return Objects.equals(caseDO.getOwnerId(), userId) || this.isStaff();
    }

    /**
     * 是否为后台审核账号（C 端 token 不共享后台登录域，异常时按无权限处理）
     */
    private boolean isStaff() {
        try {
            return StpUtil.getLoginIdDefaultNull() != null && StpUtil.hasPermission("moderation:case:manage");
        } catch (Exception e) {
            return false;
        }
    }

    private long countTodayCases(Long userId, LocalDateTime now) {
        Long count = findCaseMapper.selectCount(Wrappers.<FindCaseDO>lambdaQuery()
            .eq(FindCaseDO::getOwnerId, userId)
            .ge(FindCaseDO::getCreateTime, LocalDate.from(now).atStartOfDay()));
        return count == null ? 0L : count;
    }

    private void increaseViewCount(Long id) {
        findCaseMapper.lambdaUpdate().setSql("view_count = view_count + 1").eq(FindCaseDO::getId, id).update();
    }

    private void trackViewFull(Long caseId, Long userId) {
        UserTrackDO track = new UserTrackDO();
        track.setUserId(userId);
        track.setEventType(TrackEventEnum.VIEW_FULL.name());
        track.setTargetType(CaseConstants.TARGET_TYPE_CASE);
        track.setTargetId(caseId);
        track.setExtra(JSONUtil.toJsonStr(Map.of("ip", StrUtil.emptyIfNull(this.clientIp()))));
        track.setCreateUser(userId);
        track.setCreateTime(LocalDateTime.now());
        track.setUpdateUser(userId);
        userTrackMapper.insert(track);
    }

    private String clientIp() {
        try {
            return ServletUtils.getRequestIp();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 清除识别字段（响应删除权），返回需回收的文件 ID
     */
    private List<Long> clearIdentifiableInfo(FindCaseDO caseDO, Long operatorId) {
        List<FindCaseMediaDO> mediaList = this.listMedia(caseDO.getId());
        List<Long> fileIds = mediaList.stream()
            .map(FindCaseMediaDO::getStorageId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        findCaseMediaMapper.delete(Wrappers.<FindCaseMediaDO>lambdaQuery()
            .eq(FindCaseMediaDO::getCaseId, caseDO.getId()));
        findCaseAliasMapper.delete(Wrappers.<FindCaseAliasDO>lambdaQuery()
            .eq(FindCaseAliasDO::getCaseId, caseDO.getId()));
        // 显式置空：让档案不再能指向具体的人，仅保留编号与状态轨迹用于追溯
        findCaseMapper.lambdaUpdate()
            .set(FindCaseDO::getName, CaseConstants.ERASED_NAME)
            .set(FindCaseDO::getAddress, null)
            .set(FindCaseDO::getContactPhone, null)
            .set(FindCaseDO::getFeatures, null)
            .set(FindCaseDO::getNarrative, null)
            .set(FindCaseDO::getFamilyClues, null)
            .set(FindCaseDO::getOtherNote, null)
            .set(FindCaseDO::getClothing, null)
            .set(FindCaseDO::getBelongings, null)
            .set(FindCaseDO::getAccent, null)
            .set(FindCaseDO::getPoliceStation, null)
            .set(FindCaseDO::getCaseAcceptedNo, null)
            .set(FindCaseDO::getFollowVolunteer, null)
            .set(FindCaseDO::getHometownCode, null)
            .set(FindCaseDO::getLng, null)
            .set(FindCaseDO::getLat, null)
            .set(FindCaseDO::getDedupeKey, null)
            .set(FindCaseDO::getSourceUrl, null)
            .set(FindCaseDO::getUpdateUser, operatorId)
            .eq(FindCaseDO::getId, caseDO.getId())
            .update();
        return fileIds;
    }

    private List<FindCaseMediaDO> buildMediaList(List<CaseMediaReq> reqList, Long caseId, Long userId) {
        if (CollUtil.isEmpty(reqList)) {
            return new ArrayList<>();
        }
        LocalDateTime now = LocalDateTime.now();
        List<FindCaseMediaDO> mediaList = new ArrayList<>(reqList.size());
        for (int i = 0; i < reqList.size(); i++) {
            CaseMediaReq item = reqList.get(i);
            FindCaseMediaDO media = new FindCaseMediaDO();
            media.setCaseId(caseId);
            media.setUrl(StrUtil.trim(item.getUrl()));
            media.setType(StrUtil.blankToDefault(item.getType(), CaseConstants.MEDIA_TYPE_PHOTO));
            media.setSort(Objects.isNull(item.getSort()) ? i : item.getSort());
            media.setCreateUser(userId);
            media.setCreateTime(now);
            media.setUpdateUser(userId);
            media.setUpdateTime(now);
            mediaList.add(media);
        }
        return mediaList;
    }

    private void saveMedia(Long caseId, List<FindCaseMediaDO> mediaList, Long userId) {
        for (FindCaseMediaDO media : mediaList) {
            media.setCaseId(caseId);
            findCaseMediaMapper.insert(media);
        }
    }

    private void saveAliases(Long caseId, List<CaseAliasReq> aliasReqList, Long userId) {
        if (CollUtil.isEmpty(aliasReqList)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (CaseAliasReq aliasReq : aliasReqList) {
            if (aliasReq == null || StrUtil.isBlank(aliasReq.getName())) {
                continue;
            }
            FindCaseAliasDO alias = new FindCaseAliasDO();
            alias.setCaseId(caseId);
            alias.setName(StrUtil.trim(aliasReq.getName()));
            alias.setKind(StrUtil.blankToDefault(aliasReq.getKind(), "NICKNAME"));
            alias.setCreateUser(userId);
            alias.setCreateTime(now);
            alias.setUpdateUser(userId);
            alias.setUpdateTime(now);
            findCaseAliasMapper.insert(alias);
        }
    }

    private void replaceRelations(Long caseId,
                                  List<CaseMediaReq> mediaReqList,
                                  List<CaseAliasReq> aliasReqList,
                                  Long userId) {
        if (mediaReqList != null) {
            findCaseMediaMapper.delete(Wrappers.<FindCaseMediaDO>lambdaQuery().eq(FindCaseMediaDO::getCaseId, caseId));
            this.saveMedia(caseId, this.buildMediaList(mediaReqList, caseId, userId), userId);
        }
        if (aliasReqList != null) {
            findCaseAliasMapper.delete(Wrappers.<FindCaseAliasDO>lambdaQuery().eq(FindCaseAliasDO::getCaseId, caseId));
            this.saveAliases(caseId, aliasReqList, userId);
        }
    }

    private List<FindCaseMediaDO> listMedia(Long caseId) {
        return findCaseMediaMapper.lambdaQuery()
            .eq(FindCaseMediaDO::getCaseId, caseId)
            .orderByAsc(FindCaseMediaDO::getSort)
            .orderByAsc(FindCaseMediaDO::getId)
            .list();
    }

    /**
     * 各状态计数
     */
    private CaseStatusSummaryResp summarize(Long userId) {
        CaseStatusSummaryResp summary = new CaseStatusSummaryResp();
        List<Map<String, Object>> rows = findCaseMapper.selectMaps(Wrappers.<FindCaseDO>query()
            .select("status AS statusValue", "COUNT(1) AS total")
            .eq("owner_id", userId)
            .groupBy("status"));
        if (CollUtil.isEmpty(rows)) {
            return summary;
        }
        for (Map<String, Object> row : rows) {
            CaseStatusEnum status = CaseStatusEnum.of(Objects.toString(row.get("statusValue"), null));
            long total = Long.parseLong(Objects.toString(row.get("total"), "0"));
            if (status == null) {
                continue;
            }
            switch (status) {
                case DRAFT -> summary.setDraft(total);
                case PENDING_REVIEW -> summary.setPendingReview(total);
                case PUBLISHED -> summary.setPublished(total);
                case REJECTED -> summary.setRejected(total);
                case ARCHIVED -> summary.setArchived(total);
                case FOUND -> summary.setFound(total);
                case OFFLINE -> summary.setOffline(total);
                default -> {
                }
            }
        }
        return summary;
    }

    /**
     * 档案可编辑范围
     */
    private enum EditScope {
        /**
         * 全量可改
         */
        FULL,

        /**
         * 仅可补充信息
         */
        SUPPLEMENT,

        /**
         * 不可修改
         */
        NONE
    }
}
