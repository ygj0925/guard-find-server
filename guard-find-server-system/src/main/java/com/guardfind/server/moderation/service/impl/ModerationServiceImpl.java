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

package com.guardfind.server.moderation.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.common.context.UserContextHolder;
import com.guardfind.server.findcase.enums.CaseOperatorTypeEnum;
import com.guardfind.server.findcase.enums.CaseStatusEnum;
import com.guardfind.server.findcase.mapper.FindCaseMapper;
import com.guardfind.server.findcase.model.entity.FindCaseDO;
import com.guardfind.server.findcase.service.CaseStatusService;
import com.guardfind.server.lead.constant.LeadConstants;
import com.guardfind.server.lead.enums.LeadStatusEnum;
import com.guardfind.server.lead.mapper.LeadMapper;
import com.guardfind.server.lead.model.entity.LeadDO;
import com.guardfind.server.moderation.constant.ModerationConstants;
import com.guardfind.server.moderation.enums.ModerationBizTypeEnum;
import com.guardfind.server.moderation.enums.PunishmentTypeEnum;
import com.guardfind.server.moderation.enums.ReportHandleActionEnum;
import com.guardfind.server.moderation.enums.ReportStatusEnum;
import com.guardfind.server.moderation.enums.ReportTargetTypeEnum;
import com.guardfind.server.moderation.mapper.ReportMapper;
import com.guardfind.server.moderation.mapper.UserPunishmentMapper;
import com.guardfind.server.moderation.model.entity.ReportDO;
import com.guardfind.server.moderation.model.entity.UserPunishmentDO;
import com.guardfind.server.moderation.model.query.ModerationQuery;
import com.guardfind.server.moderation.model.query.ReportQueueQuery;
import com.guardfind.server.moderation.model.req.ModerationApproveReq;
import com.guardfind.server.moderation.model.req.ModerationRejectReq;
import com.guardfind.server.moderation.model.req.ReportHandleReq;
import com.guardfind.server.moderation.model.resp.ModerationItemResp;
import com.guardfind.server.moderation.model.resp.ReportQueueItemResp;
import com.guardfind.server.moderation.service.ModerationService;
import com.guardfind.server.notification.enums.NotificationEventEnum;
import com.guardfind.server.notification.service.AppMessageService;
import com.guardfind.server.system.service.DictItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.starter.core.constant.StringConstants;
import top.continew.starter.core.util.validation.CheckUtils;
import top.continew.starter.core.util.validation.ValidationUtils;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.LabelValueResp;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 内容治理业务实现（后台）
 *
 * <p>
 * 队列把"待审档案"与"高风险线索"收敛成同一形状，但结论各自落回自己的状态漏斗：档案一律经
 * {@link CaseStatusService#change} 变更（保证状态轨迹与通知同事务），本服务不直接改档案状态字段；
 * 线索只改自身的 {@code highRisk}/{@code status}。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationServiceImpl implements ModerationService {

    /**
     * 审核通过理由码（写入状态轨迹）
     */
    private static final String APPROVE_REASON_CODE = "APPROVED";

    /**
     * 区划筛选时回查档案 ID 的上限（避免超长 IN 子句）
     */
    private static final int REGION_CASE_ID_LIMIT = 1000;

    /**
     * 队列摘要长度
     */
    private static final int SUMMARY_LENGTH = 80;

    /**
     * 队列默认页长
     */
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final FindCaseMapper findCaseMapper;
    private final LeadMapper leadMapper;
    private final ReportMapper reportMapper;
    private final UserPunishmentMapper userPunishmentMapper;
    private final AppUserMapper appUserMapper;
    private final CaseStatusService caseStatusService;
    private final AppMessageService appMessageService;
    private final DictItemService dictItemService;

    @Override
    public PageResp<ModerationItemResp> pending(ModerationQuery query, PageQuery pageQuery) {
        ModerationQuery condition = ObjectUtil.defaultIfNull(query, new ModerationQuery());
        ModerationBizTypeEnum bizType = this.resolveQueueBizType(condition.getBizType());
        if (bizType == ModerationBizTypeEnum.LEAD) {
            return this.pendingLeads(condition, pageQuery);
        }
        Page<FindCaseDO> page = this.page(pageQuery);
        LambdaQueryWrapper<FindCaseDO> wrapper = Wrappers.<FindCaseDO>lambdaQuery()
            .eq(FindCaseDO::getStatus, CaseStatusEnum.PENDING_REVIEW)
            .eq(StrUtil.isNotBlank(condition.getProvinceCode()), FindCaseDO::getProvinceCode, condition
                .getProvinceCode())
            .eq(StrUtil.isNotBlank(condition.getCityCode()), FindCaseDO::getCityCode, condition.getCityCode())
            .orderByAsc(FindCaseDO::getCreateTime)
            .orderByAsc(FindCaseDO::getId);
        findCaseMapper.selectPage(page, wrapper);
        List<ModerationItemResp> items = page.getRecords().stream().map(this::toCaseItem).collect(Collectors.toList());
        return this.buildPage(items, page.getTotal());
    }

    @Override
    public PageResp<ReportQueueItemResp> reportPage(ReportQueueQuery query, PageQuery pageQuery) {
        ReportQueueQuery condition = ObjectUtil.defaultIfNull(query, new ReportQueueQuery());
        // 不带条件时只给待处理队列，已处置记录需显式指定状态才出现在列表里
        ReportStatusEnum status = condition.getStatus() == null && condition.getTargetType() == null
            ? ReportStatusEnum.PENDING
            : condition.getStatus();
        Page<ReportDO> page = this.page(pageQuery);
        LambdaQueryWrapper<ReportDO> wrapper = Wrappers.<ReportDO>lambdaQuery()
            .eq(status != null, ReportDO::getStatus, status)
            .eq(condition.getTargetType() != null, ReportDO::getTargetType, condition.getTargetType())
            .orderByAsc(ReportDO::getCreateTime)
            .orderByAsc(ReportDO::getId);
        reportMapper.selectPage(page, wrapper);
        List<ReportDO> reports = page.getRecords();
        Map<Long, LeadDO> leadMap = this.groupLeads(reports);
        List<Long> caseIds = this.caseIdsOfReports(reports, leadMap);
        Map<Long, FindCaseDO> caseMap = this.groupCaseMap(caseIds);
        Map<Long, String> nicknameMap = this.groupNicknames(this.nicknameIds(reports));
        List<ReportQueueItemResp> items = reports.stream()
            .map(report -> this.toReportItem(report, caseMap, leadMap, nicknameMap))
            .collect(Collectors.toList());
        return this.buildPage(items, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(ModerationBizTypeEnum bizType, Long id, ModerationApproveReq req) {
        ModerationBizTypeEnum type = this.requireSupported(bizType);
        Long operatorId = this.currentOperator();
        String remark = req == null ? null : StrUtil.trimToNull(req.getRemark());
        if (type == ModerationBizTypeEnum.LEAD) {
            this.approveLead(id, operatorId, remark);
            return;
        }
        FindCaseDO caseDO = this.getCase(id);
        CheckUtils.throwIf(caseDO.getStatus() == CaseStatusEnum.PUBLISHED, "该档案已发布，无需重复审核");
        String reason = StrUtil.blankToDefault(remark, "内容审核通过");
        // 状态变更 + 状态轨迹 + 发布人通知由状态漏斗统一完成
        caseStatusService
            .change(caseDO, CaseStatusEnum.PUBLISHED, APPROVE_REASON_CODE, reason, operatorId, CaseOperatorTypeEnum.SYS);
        this.saveAuditRemark(caseDO.getId(), reason, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(ModerationBizTypeEnum bizType, Long id, ModerationRejectReq req) {
        ModerationBizTypeEnum type = this.requireSupported(bizType);
        Long operatorId = this.currentOperator();
        ValidationUtils.throwIfNull(req, "请填写驳回理由");
        List<LabelValueResp> reasonItems = this.rejectReasonItems();
        String reasonCode = this.checkRejectReasonCode(req.getReasonCode(), reasonItems);
        String reason = this.buildRejectReason(reasonCode, req.getRemark(), reasonItems);
        if (type == ModerationBizTypeEnum.LEAD) {
            LeadDO leadDO = this.getLead(id);
            LeadDO update = new LeadDO();
            update.setId(id);
            update.setStatus(LeadStatusEnum.REJECTED);
            update.setHandleRemark(reason);
            update.setUpdateUser(operatorId);
            update.setUpdateTime(LocalDateTime.now());
            leadMapper.updateById(update);
            this.notifyLeadSubmitter(leadDO, "线索未通过核实", "您提供的线索经平台核实未予采纳：%s。若您掌握更多信息，欢迎重新提交。".formatted(reason));
            return;
        }
        FindCaseDO caseDO = this.getCase(id);
        CheckUtils.throwIf(caseDO.getStatus() == CaseStatusEnum.REJECTED, "该档案已驳回，无需重复操作");
        caseStatusService
            .change(caseDO, CaseStatusEnum.REJECTED, reasonCode, reason, operatorId, CaseOperatorTypeEnum.SYS);
        this.saveAuditRemark(caseDO.getId(), reason, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleReport(Long id, ReportHandleReq req) {
        Long operatorId = this.currentOperator();
        ValidationUtils.throwIfNull(req, "请填写处置结果");
        ReportHandleActionEnum action = ReportHandleActionEnum.of(req.getAction());
        ValidationUtils.throwIfNull(action, "处置动作仅支持 APPROVE_REPORT/DISMISS");
        ReportDO report = this.getReport(id);
        CheckUtils.throwIfNull(report.getTargetType(), "举报记录缺少目标类型，无法处置");
        CheckUtils.throwIf(report.getStatus() != ReportStatusEnum.PENDING, "该举报已处理（{}），无需重复处置", this
            .reportStatusDescription(report.getStatus()));
        LocalDateTime now = LocalDateTime.now();
        boolean approved = action == ReportHandleActionEnum.APPROVE_REPORT;
        String remark = StrUtil.blankToDefault(StrUtil.trimToNull(req.getRemark()), action.getDescription());
        // 1. 举报结论落库（处理人、说明、时间三要素齐备）
        ReportDO update = new ReportDO();
        update.setId(id);
        update.setStatus(approved ? ReportStatusEnum.APPROVED : ReportStatusEnum.DISMISSED);
        update.setHandleRemark(remark);
        update.setHandlerId(operatorId);
        update.setHandleTime(now);
        update.setUpdateUser(operatorId);
        update.setUpdateTime(now);
        reportMapper.updateById(update);
        Long reportedUserId = this.resolveReportedUserId(report);
        // 2. 举报成立才联动处置：下架被举报档案 / 不采信被举报线索 / 登记用户处置
        if (approved) {
            this.takeDownTarget(report, remark, operatorId, now);
            this.savePunishment(report, req.getPunishment(), reportedUserId, remark, operatorId, now);
        }
        // 3. 双方回执：举报人要看到结论，被举报人要知道做了什么
        this.notifyReportResult(report, reportedUserId, approved, remark);
    }

    /**
     * 高风险线索人工核实通过：撤销高风险标记，恢复为普通线索展示给发布人
     *
     * @param id         线索 ID
     * @param operatorId 审核人
     * @param remark     审核备注
     */
    private void approveLead(Long id, Long operatorId, String remark) {
        LeadDO leadDO = this.getLead(id);
        CheckUtils.throwIf(!Boolean.TRUE.equals(leadDO.getHighRisk()), "该线索未被标记为高风险，无需人工核实");
        LeadDO update = new LeadDO();
        update.setId(id);
        update.setHighRisk(false);
        update.setHandleRemark(StrUtil.blankToDefault(remark, "人工核实通过，已作为普通线索展示给档案发布人"));
        update.setUpdateUser(operatorId);
        update.setUpdateTime(LocalDateTime.now());
        leadMapper.updateById(update);
        this.notifyLeadSubmitter(leadDO, "线索已通过人工核实", "您提供的线索经平台人工核实未见异常，已作为普通线索展示给档案发布人。平台提醒：任何以找回为由索要钱财的行为都是诈骗。");
    }

    /**
     * 队列业务类型解析：为空默认档案；采集候选归属采集域审批
     *
     * @param bizType 入参
     * @return 业务类型
     */
    private ModerationBizTypeEnum resolveQueueBizType(String bizType) {
        if (StrUtil.isBlank(bizType)) {
            return ModerationBizTypeEnum.CASE;
        }
        ModerationBizTypeEnum type = ModerationBizTypeEnum.of(bizType);
        ValidationUtils.throwIfNull(type, "业务类型仅支持 CASE/LEAD");
        return requireCollectedFree(type);
    }

    private ModerationBizTypeEnum requireSupported(ModerationBizTypeEnum bizType) {
        ValidationUtils.throwIfNull(bizType, "业务类型仅支持 CASE/LEAD");
        return requireCollectedFree(bizType);
    }

    private static ModerationBizTypeEnum requireCollectedFree(ModerationBizTypeEnum bizType) {
        CheckUtils
            .throwIf(bizType == ModerationBizTypeEnum.COLLECT_CANDIDATE, "采集候选请通过采集审批接口（/system/collect/candidate/{id}/approve）处理，内容治理队列不代为审批");
        return bizType;
    }

    /**
     * 高风险线索队列（区划筛选时先按档案圈定 ID 集合）
     *
     * @param condition 查询条件
     * @param pageQuery 分页参数
     * @return 分页响应
     */
    private PageResp<ModerationItemResp> pendingLeads(ModerationQuery condition, PageQuery pageQuery) {
        boolean regionFiltered = StrUtil.isNotBlank(condition.getProvinceCode()) || StrUtil.isNotBlank(condition
            .getCityCode());
        List<Long> caseIds = regionFiltered ? this.listCaseIdsByRegion(condition) : Collections.emptyList();
        if (regionFiltered && CollUtil.isEmpty(caseIds)) {
            return this.buildPage(new ArrayList<>(), 0L);
        }
        Page<LeadDO> page = this.page(pageQuery);
        LambdaQueryWrapper<LeadDO> wrapper = Wrappers.<LeadDO>lambdaQuery()
            .eq(LeadDO::getHighRisk, true)
            .eq(LeadDO::getStatus, LeadStatusEnum.PENDING)
            .in(regionFiltered, LeadDO::getCaseId, caseIds)
            .orderByAsc(LeadDO::getCreateTime)
            .orderByAsc(LeadDO::getId);
        leadMapper.selectPage(page, wrapper);
        Map<Long, FindCaseDO> caseMap = this.groupCaseMap(page.getRecords()
            .stream()
            .map(LeadDO::getCaseId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList()));
        List<ModerationItemResp> items = page.getRecords()
            .stream()
            .map(lead -> this.toLeadItem(lead, caseMap.get(lead.getCaseId())))
            .collect(Collectors.toList());
        return this.buildPage(items, page.getTotal());
    }

    private List<Long> listCaseIdsByRegion(ModerationQuery condition) {
        List<FindCaseDO> cases = findCaseMapper.lambdaQuery()
            .select(FindCaseDO::getId)
            .eq(StrUtil.isNotBlank(condition.getProvinceCode()), FindCaseDO::getProvinceCode, condition
                .getProvinceCode())
            .eq(StrUtil.isNotBlank(condition.getCityCode()), FindCaseDO::getCityCode, condition.getCityCode())
            .last("LIMIT " + REGION_CASE_ID_LIMIT)
            .list();
        if (cases.size() >= REGION_CASE_ID_LIMIT) {
            log.warn("区划筛选命中的档案数已达上限 {}，高风险线索队列可能不完整，请缩小筛选范围", REGION_CASE_ID_LIMIT);
        }
        return cases.stream().map(FindCaseDO::getId).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private ModerationItemResp toCaseItem(FindCaseDO caseDO) {
        ModerationItemResp item = new ModerationItemResp();
        item.setBizType(ModerationBizTypeEnum.CASE);
        item.setBizId(caseDO.getId());
        item.setTitle(StrUtil.blankToDefault(caseDO.getName(), "寻人档案"));
        item.setSummary(this.summarize(caseDO));
        item.setStatusCode(caseDO.getStatus() == null ? null : caseDO.getStatus().name());
        item.setStatusDescription(caseDO.getStatus() == null ? null : caseDO.getStatus().getDescription());
        item.setSubmitTime(caseDO.getCreateTime());
        item.setProvinceCode(caseDO.getProvinceCode());
        item.setCityCode(caseDO.getCityCode());
        item.setRelatedUserId(caseDO.getOwnerId());
        item.setHighRisk(false);
        item.setReportCount(caseDO.getReportCount());
        item.setLeadCount(caseDO.getLeadCount());
        return item;
    }

    private ModerationItemResp toLeadItem(LeadDO leadDO, FindCaseDO caseDO) {
        ModerationItemResp item = new ModerationItemResp();
        item.setBizType(ModerationBizTypeEnum.LEAD);
        item.setBizId(leadDO.getId());
        item.setTitle("线索：%s".formatted(caseDO == null ? "已删除档案" : StrUtil.blankToDefault(caseDO.getName(), "寻人档案")));
        item.setSummary(StrUtil.maxLength(StrUtil.emptyIfNull(leadDO.getDescription()), SUMMARY_LENGTH));
        item.setStatusCode(leadDO.getStatus() == null ? null : leadDO.getStatus().name());
        // 高风险线索在家属端与队列中统一表现为"待核实"，避免被当成普通线索直接联系
        item.setStatusDescription("待核实");
        item.setSubmitTime(leadDO.getCreateTime());
        item.setRelatedUserId(leadDO.getSubmitterId());
        item.setHighRisk(Boolean.TRUE.equals(leadDO.getHighRisk()));
        return item;
    }

    private String summarize(FindCaseDO caseDO) {
        String summary = StrUtil.join(StringConstants.SPACE, caseDO.getFeatures(), caseDO.getNarrative(), caseDO
            .getFamilyClues());
        return StrUtil.maxLength(StrUtil.trimToEmpty(summary), SUMMARY_LENGTH);
    }

    /**
     * 举报队列条目装配
     *
     * @param report      举报实体
     * @param caseMap     档案字典（含线索所属档案）
     * @param leadMap     线索字典
     * @param nicknameMap 举报人昵称字典
     * @return 队列条目
     */
    private ReportQueueItemResp toReportItem(ReportDO report,
                                             Map<Long, FindCaseDO> caseMap,
                                             Map<Long, LeadDO> leadMap,
                                             Map<Long, String> nicknameMap) {
        ReportQueueItemResp item = new ReportQueueItemResp();
        item.setId(report.getId());
        item.setReporterId(report.getReporterId());
        item.setReporterNickname(nicknameMap.get(report.getReporterId()));
        item.setTargetType(report.getTargetType());
        item.setTargetId(report.getTargetId());
        item.setReasonType(report.getReasonType());
        item.setDescription(report.getDescription());
        item.setStatus(report.getStatus());
        item.setHitCount(report.getHitCount());
        item.setHandleRemark(report.getHandleRemark());
        item.setHandlerId(report.getHandlerId());
        item.setHandleTime(report.getHandleTime());
        item.setCreateTime(report.getCreateTime());
        FindCaseDO caseDO = report.getTargetType() == ReportTargetTypeEnum.CASE
            ? caseMap.get(report.getTargetId())
            : null;
        LeadDO leadDO = report.getTargetType() == ReportTargetTypeEnum.LEAD ? leadMap.get(report.getTargetId()) : null;
        if (report.getTargetType() == null) {
            // 脏数据不阻断整页队列，仅缺对象信息
            return item;
        }
        switch (report.getTargetType()) {
            case CASE -> {
                item.setBizType(ModerationBizTypeEnum.CASE);
                item.setTargetName(caseDO == null ? null : caseDO.getName());
                item.setReportedUserId(caseDO == null ? null : caseDO.getOwnerId());
            }
            case LEAD -> {
                item.setBizType(ModerationBizTypeEnum.LEAD);
                item.setTargetName(leadDO == null
                    ? null
                    : StrUtil.maxLength(StrUtil.emptyIfNull(leadDO.getDescription()), SUMMARY_LENGTH));
                item.setReportedUserId(leadDO == null ? null : leadDO.getSubmitterId());
            }
            case USER -> {
                item.setReportedUserId(report.getTargetId());
                item.setTargetName(nicknameMap.get(report.getTargetId()));
            }
            default -> log.debug("举报 [{}] 目标类型为空，跳过对象回查", report.getId());
        }
        return item;
    }

    /**
     * 举报成立后的联动处置：档案下架、线索不予采信
     *
     * @param report     举报
     * @param remark     处置说明
     * @param operatorId 处理人
     * @param now        处理时间
     */
    private void takeDownTarget(ReportDO report, String remark, Long operatorId, LocalDateTime now) {
        switch (report.getTargetType()) {
            case CASE -> {
                FindCaseDO caseDO = findCaseMapper.selectById(report.getTargetId());
                if (caseDO == null) {
                    log.warn("举报 [{}] 指向的档案 [{}] 已不存在，跳过下架", report.getId(), report.getTargetId());
                    return;
                }
                if (!CaseStatusEnum.canTransfer(caseDO.getStatus(), CaseStatusEnum.OFFLINE)) {
                    // 未发布的档案本就对外不可见，不制造一次非法状态跃迁
                    log.info("举报 [{}] 指向的档案当前为 [{}]，无需下架", report.getId(), caseDO.getStatus());
                    return;
                }
                caseStatusService.change(caseDO, CaseStatusEnum.OFFLINE, report.getReasonType(), "举报成立下架：%s"
                    .formatted(remark), operatorId, CaseOperatorTypeEnum.SYS);
            }
            case LEAD -> {
                LeadDO leadDO = leadMapper.selectById(report.getTargetId());
                if (leadDO == null) {
                    return;
                }
                LeadDO update = new LeadDO();
                update.setId(leadDO.getId());
                update.setStatus(LeadStatusEnum.REJECTED);
                update.setHandleRemark("举报成立，线索不予采信：%s".formatted(remark));
                update.setUpdateUser(operatorId);
                update.setUpdateTime(now);
                leadMapper.updateById(update);
                this.notifyLeadSubmitter(leadDO, "线索已被下架", "您提供的线索因举报核实被下架：%s。".formatted(remark));
            }
            case USER -> log.info("举报 [{}] 目标为用户，处置结论仅登记在 gf_user_punishment", report.getId());
            default -> log.debug("举报 [{}] 目标类型为空，无联动处置", report.getId());
        }
    }

    /**
     * 登记用户处置记录
     *
     * @param report         举报
     * @param punishmentReq  处置入参（可为空）
     * @param reportedUserId 被举报人
     * @param remark         处置说明
     * @param operatorId     处理人
     * @param now            处理时间
     */
    private void savePunishment(ReportDO report,
                                ReportHandleReq.PunishmentReq punishmentReq,
                                Long reportedUserId,
                                String remark,
                                Long operatorId,
                                LocalDateTime now) {
        if (punishmentReq == null || StrUtil.isBlank(punishmentReq.getType())) {
            return;
        }
        ValidationUtils.throwIfNull(reportedUserId, "被举报对象无可处置的用户，请核查举报目标");
        PunishmentTypeEnum type = PunishmentTypeEnum.of(punishmentReq.getType());
        ValidationUtils.throwIfNull(type, "处置类型仅支持 MUTE/BAN");
        Integer days = this.resolvePunishmentDays(type, punishmentReq.getDays());
        UserPunishmentDO punishment = new UserPunishmentDO();
        punishment.setUserId(reportedUserId);
        punishment.setType(type);
        punishment.setDays(days);
        punishment.setStartTime(now);
        punishment.setEndTime(days == null ? null : now.plusDays(days));
        punishment.setReason("举报成立（理由 %s）：%s".formatted(report.getReasonType(), remark));
        punishment.setReportId(report.getId());
        punishment.setCreateUser(operatorId);
        punishment.setCreateTime(now);
        punishment.setUpdateUser(operatorId);
        punishment.setUpdateTime(now);
        userPunishmentMapper.insert(punishment);
        log.info("已登记用户处置：用户 {}，类型 {}，时长 {}，关联举报 {}", reportedUserId, type.name(), days == null
            ? "永久"
            : days + " 天", report.getId());
    }

    /**
     * 处置时长归一：空取该类型默认值，-1 表示永久
     *
     * @param type 处置类型
     * @param days 入参天数
     * @return 天数，永久时为 null
     */
    private Integer resolvePunishmentDays(PunishmentTypeEnum type, Integer days) {
        if (days == null) {
            return type.getDefaultDays();
        }
        ValidationUtils.throwIf(days < -1 || days == 0, "处置时长仅支持 -1（永久）或正整数天");
        return days == -1 ? null : days;
    }

    /**
     * 举报结果双向回执
     *
     * @param report         举报
     * @param reportedUserId 被举报人
     * @param approved       举报是否成立
     * @param remark         处理说明
     */
    private void notifyReportResult(ReportDO report, Long reportedUserId, boolean approved, String remark) {
        String reporterContent = "您对 %s 的举报（理由：%s）已%s。处理说明：%s。感谢您帮助维护社区环境。".formatted(this.targetLabel(report), report
            .getReasonType(), approved ? "核实成立" : "核实不成立", remark);
        appMessageService.send(report
            .getReporterId(), NotificationEventEnum.REPORT_RESULT, ModerationConstants.MESSAGE_BIZ_TYPE_REPORT, report
                .getId(), "您的举报已处理", reporterContent);
        if (reportedUserId == null || Objects.equals(reportedUserId, report.getReporterId())) {
            return;
        }
        String reportedContent = "您发布的内容被他人举报（理由：%s），平台核实%s。处理说明：%s。如有异议请联系客服。".formatted(report
            .getReasonType(), approved ? "属实，相关内容已作下架处理" : "不成立，该内容保持展示", remark);
        appMessageService
            .send(reportedUserId, NotificationEventEnum.REPORT_RESULT, ModerationConstants.MESSAGE_BIZ_TYPE_REPORT, report
                .getId(), "您的内容被举报并已处理", reportedContent);
    }

    private String targetLabel(ReportDO report) {
        ReportTargetTypeEnum targetType = ObjectUtil.defaultIfNull(report.getTargetType(), ReportTargetTypeEnum.CASE);
        return "%s（ID：%s）".formatted(targetType.getDescription(), report.getTargetId());
    }

    /**
     * 被举报人解析：档案→发布人，线索→提交人，用户→目标本身
     *
     * @param report 举报
     * @return 被举报的 App 用户 ID，无法确定时为 null
     */
    private Long resolveReportedUserId(ReportDO report) {
        return switch (report.getTargetType()) {
            case CASE -> {
                FindCaseDO caseDO = findCaseMapper.selectById(report.getTargetId());
                yield caseDO == null ? null : caseDO.getOwnerId();
            }
            case LEAD -> {
                LeadDO leadDO = leadMapper.selectById(report.getTargetId());
                yield leadDO == null ? null : leadDO.getSubmitterId();
            }
            case USER -> report.getTargetId();
        };
    }

    /**
     * 驳回理由字典项（字典缺失时直接拒绝审核动作，不放行任意理由码）
     *
     * @return 字典项列表
     */
    private List<LabelValueResp> rejectReasonItems() {
        List<LabelValueResp> items = dictItemService.listByDictCode(ModerationConstants.DICT_REJECT_REASON);
        ValidationUtils.throwIf(CollUtil
            .isEmpty(items), "驳回理由字典 {} 未配置，请先在字典管理中维护", ModerationConstants.DICT_REJECT_REASON);
        return items;
    }

    private String checkRejectReasonCode(String reasonCode, List<LabelValueResp> items) {
        String code = StrUtil.trimToEmpty(reasonCode).toUpperCase();
        ValidationUtils.throwIf(StrUtil.isBlank(code), "驳回理由码不能为空");
        Set<String> values = items.stream()
            .map(item -> StrUtil.trimToEmpty(Objects.toString(item.getValue(), "")).toUpperCase())
            .filter(StrUtil::isNotBlank)
            .collect(Collectors.toSet());
        ValidationUtils.throwIf(!values
            .contains(code), "驳回理由码不在字典 {} 范围内，可选值：{}", ModerationConstants.DICT_REJECT_REASON, String
                .join("、", values));
        return code;
    }

    /**
     * 驳回文案：字典标签 + 自由文本，既能按理由统计，也保留审核人的具体意见
     *
     * @param reasonCode 理由码
     * @param remark     补充说明
     * @param items      字典项
     * @return 驳回文案
     */
    private String buildRejectReason(String reasonCode, String remark, List<LabelValueResp> items) {
        String label = items.stream()
            .filter(item -> StrUtil.equalsIgnoreCase(Objects.toString(item.getValue(), ""), reasonCode))
            .findFirst()
            .map(item -> StrUtil.blankToDefault(item.getLabel(), reasonCode))
            .orElse(reasonCode);
        return StrUtil.isBlank(remark) ? label : "%s（%s）".formatted(label, StrUtil.trim(remark));
    }

    /**
     * 冗余最近一次审核意见到档案（审核人回看时不必翻状态轨迹）
     *
     * @param caseId     档案 ID
     * @param reason     审核意见
     * @param operatorId 审核人
     */
    private void saveAuditRemark(Long caseId, String reason, Long operatorId) {
        FindCaseDO update = new FindCaseDO();
        update.setId(caseId);
        update.setAuditRemark(reason);
        update.setUpdateUser(operatorId);
        update.setUpdateTime(LocalDateTime.now());
        findCaseMapper.updateById(update);
    }

    private void notifyLeadSubmitter(LeadDO leadDO, String title, String content) {
        if (leadDO.getSubmitterId() == null) {
            return;
        }
        appMessageService.send(leadDO
            .getSubmitterId(), NotificationEventEnum.LEAD_REPLIED, LeadConstants.MESSAGE_BIZ_TYPE, leadDO
                .getId(), title, content);
    }

    private FindCaseDO getCase(Long id) {
        FindCaseDO caseDO = findCaseMapper.selectById(id);
        CheckUtils.throwIfNull(caseDO, "档案不存在或已删除");
        return caseDO;
    }

    private LeadDO getLead(Long id) {
        LeadDO leadDO = leadMapper.selectById(id);
        CheckUtils.throwIfNull(leadDO, "线索不存在或已删除");
        return leadDO;
    }

    private ReportDO getReport(Long id) {
        ReportDO report = reportMapper.selectById(id);
        CheckUtils.throwIfNull(report, "举报不存在或已删除");
        return report;
    }

    private Long currentOperator() {
        Long operatorId = UserContextHolder.getUserId();
        CheckUtils.throwIfNull(operatorId, "请先登录后台账号");
        return operatorId;
    }

    private String reportStatusDescription(ReportStatusEnum status) {
        return status == null ? "未知" : status.getDescription();
    }

    private List<Long> caseIdsOfReports(List<ReportDO> reports, Map<Long, LeadDO> leadMap) {
        if (CollUtil.isEmpty(reports)) {
            return new ArrayList<>();
        }
        List<Long> caseIds = reports.stream()
            .filter(report -> report.getTargetType() == ReportTargetTypeEnum.CASE)
            .map(ReportDO::getTargetId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        // 线索目标也回查其所属档案，供队列展示"这条线索挂在哪个人名下"
        leadMap.values().stream().map(LeadDO::getCaseId).filter(Objects::nonNull).forEach(caseIds::add);
        return caseIds.stream().distinct().collect(Collectors.toList());
    }

    private Map<Long, FindCaseDO> groupCaseMap(List<Long> caseIds) {
        if (CollUtil.isEmpty(caseIds)) {
            return new HashMap<>(4);
        }
        return findCaseMapper.selectList(Wrappers.<FindCaseDO>lambdaQuery().in(FindCaseDO::getId, caseIds))
            .stream()
            .collect(Collectors.toMap(FindCaseDO::getId, Function.identity(), (existing,
                                                                               replacement) -> existing, HashMap::new));
    }

    private Map<Long, LeadDO> groupLeads(List<ReportDO> reports) {
        List<Long> leadIds = CollUtil.isEmpty(reports)
            ? new ArrayList<>()
            : reports.stream()
                .filter(report -> report.getTargetType() == ReportTargetTypeEnum.LEAD)
                .map(ReportDO::getTargetId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (CollUtil.isEmpty(leadIds)) {
            return Collections.emptyMap();
        }
        return leadMapper.selectList(Wrappers.<LeadDO>lambdaQuery().in(LeadDO::getId, leadIds))
            .stream()
            .collect(Collectors.toMap(LeadDO::getId, Function.identity(), (existing, replacement) -> existing));
    }

    /**
     * 需要回查昵称的用户 ID：举报人 + 被举报用户（USER 目标）
     *
     * @param reports 举报列表
     * @return 用户 ID 列表
     */
    private List<Long> nicknameIds(List<ReportDO> reports) {
        if (CollUtil.isEmpty(reports)) {
            return new ArrayList<>();
        }
        List<Long> userIds = reports.stream()
            .map(ReportDO::getReporterId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        reports.stream()
            .filter(report -> report.getTargetType() == ReportTargetTypeEnum.USER)
            .map(ReportDO::getTargetId)
            .filter(Objects::nonNull)
            .forEach(userIds::add);
        return userIds.stream().distinct().collect(Collectors.toList());
    }

    private Map<Long, String> groupNicknames(List<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        return appUserMapper.selectList(Wrappers.<AppUserDO>lambdaQuery().in(AppUserDO::getId, userIds))
            .stream()
            .collect(Collectors.toMap(AppUserDO::getId, user -> StrUtil.emptyIfNull(user.getNickname()), (existing,
                                                                                                          replacement) -> existing));
    }

    private <T> PageResp<T> buildPage(List<T> list, long total) {
        PageResp<T> resp = new PageResp<>();
        resp.setList(list);
        resp.setTotal(total);
        return resp;
    }

    private <T> Page<T> page(PageQuery pageQuery) {
        int page = pageQuery == null || pageQuery.getPage() == null ? 1 : pageQuery.getPage();
        int size = pageQuery == null || pageQuery.getSize() == null
            ? DEFAULT_PAGE_SIZE
            : Math.min(pageQuery.getSize(), ModerationConstants.MAX_PAGE_SIZE);
        return new Page<>(page, size);
    }
}
