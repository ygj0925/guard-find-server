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
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.findcase.enums.CaseStatusEnum;
import com.guardfind.server.findcase.mapper.FindCaseMapper;
import com.guardfind.server.findcase.model.entity.FindCaseDO;
import com.guardfind.server.lead.mapper.LeadMapper;
import com.guardfind.server.lead.model.entity.LeadDO;
import com.guardfind.server.moderation.constant.ModerationConstants;
import com.guardfind.server.moderation.enums.ReportStatusEnum;
import com.guardfind.server.moderation.enums.ReportTargetTypeEnum;
import com.guardfind.server.moderation.mapper.ReportMapper;
import com.guardfind.server.moderation.model.entity.ReportDO;
import com.guardfind.server.moderation.model.req.ReportReq;
import com.guardfind.server.moderation.model.resp.ReportPageResp;
import com.guardfind.server.moderation.model.resp.ReportResp;
import com.guardfind.server.moderation.service.ReportService;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 举报业务实现
 *
 * <p>
 * 去重规则与唯一键 {@code uk_reporter_target} 一致：同一举报人对同一目标只有一条记录，重复举报只累加
 * {@code hitCount}。累加到阈值时把被举报档案的 {@code report_count} 抬一格并告警留痕，使队列排序与广场降权有据可依。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    /**
     * 契约 §6 约定的举报理由兜底集（字典缺失时不放行任意值）
     */
    private static final Set<String> FALLBACK_REASON_TYPES = Set
        .of("FRAUD", "FALSE_INFO", "PORN", "ILLEGAL", "HARASSMENT", "REPEAT", "OTHER");

    /**
     * 目标名称摘要长度
     */
    private static final int TARGET_SUMMARY_LENGTH = 40;

    private final ReportMapper reportMapper;
    private final FindCaseMapper findCaseMapper;
    private final LeadMapper leadMapper;
    private final AppUserMapper appUserMapper;
    private final DictItemService dictItemService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportResp report(ReportReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ValidationUtils.throwIfNull(req, "请填写举报信息");
        ReportTargetTypeEnum targetType = ReportTargetTypeEnum.of(req.getTargetType());
        ValidationUtils.throwIfNull(targetType, "举报目标类型仅支持 CASE/LEAD/USER");
        ValidationUtils.throwIfNull(req.getTargetId(), "请填写举报目标");
        this.checkReasonType(req.getReasonType());
        TargetRef target = this.resolveTarget(targetType, req.getTargetId());
        LocalDateTime now = LocalDateTime.now();
        ReportDO existing = this.getExistingReport(userId, targetType, req.getTargetId());
        ReportDO report = existing == null
            ? this.createReport(req, targetType, target, userId, now)
            : this.mergeReport(existing, req, userId, now);
        this.surfaceToQueue(report, target);
        return this.toResp(report, target.name());
    }

    @Override
    public ReportPageResp mine(PageQuery pageQuery) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        LambdaQueryWrapper<ReportDO> wrapper = Wrappers.<ReportDO>lambdaQuery()
            .eq(ReportDO::getReporterId, userId)
            .orderByDesc(ReportDO::getCreateTime)
            .orderByDesc(ReportDO::getId);
        int page = pageQuery == null || pageQuery.getPage() == null ? 1 : pageQuery.getPage();
        int size = pageQuery == null || pageQuery.getSize() == null ? 10 : pageQuery.getSize();
        Page<ReportDO> result = reportMapper.selectPage(new Page<>(page, size), wrapper);
        return ReportPageResp.of(this.toRespList(result.getRecords()), result.getTotal());
    }

    /**
     * 举报理由必须在字典 {@code gf_report_reason} 内（字典未就绪时按契约兜底集校验，不放行任意值）
     *
     * @param reasonType 举报理由
     */
    private void checkReasonType(String reasonType) {
        String value = StrUtil.trimToEmpty(reasonType).toUpperCase();
        ValidationUtils.throwIf(StrUtil.isBlank(value), "举报理由不能为空");
        Set<String> allowed = this.dictReasonTypes();
        ValidationUtils.throwIf(!allowed.contains(value), "举报理由不在可选范围内");
    }

    private Set<String> dictReasonTypes() {
        List<LabelValueResp> items = dictItemService.listByDictCode(ModerationConstants.DICT_REPORT_REASON);
        Set<String> values = new HashSet<>();
        if (CollUtil.isNotEmpty(items)) {
            items.forEach(item -> values.add(StrUtil.trimToEmpty(Objects.toString(item.getValue(), "")).toUpperCase()));
            values.remove(StringConstants.EMPTY);
        }
        return values.isEmpty() ? FALLBACK_REASON_TYPES : values;
    }

    /**
     * 校验举报目标存在（避免对不存在的对象刷举报）
     *
     * @param targetType 目标类型
     * @param targetId   目标 ID
     * @return 目标引用（含所属档案 ID 与展示名）
     */
    private TargetRef resolveTarget(ReportTargetTypeEnum targetType, Long targetId) {
        return switch (targetType) {
            case CASE -> {
                FindCaseDO caseDO = findCaseMapper.selectById(targetId);
                CheckUtils.throwIfNull(caseDO, "举报对象不存在或已删除");
                CheckUtils.throwIf(caseDO.getStatus() == CaseStatusEnum.ERASED, "举报对象不存在或已删除");
                yield new TargetRef(caseDO.getId(), StrUtil.blankToDefault(caseDO.getName(), "寻人档案"));
            }
            case LEAD -> {
                LeadDO leadDO = leadMapper.selectById(targetId);
                CheckUtils.throwIfNull(leadDO, "举报对象不存在或已删除");
                yield new TargetRef(leadDO.getCaseId(), StrUtil.maxLength(StrUtil.emptyIfNull(leadDO
                    .getDescription()), TARGET_SUMMARY_LENGTH));
            }
            case USER -> {
                AppUserDO user = appUserMapper.selectById(targetId);
                CheckUtils.throwIfNull(user, "举报对象不存在或已删除");
                yield new TargetRef(null, StrUtil.blankToDefault(user.getNickname(), "用户"));
            }
        };
    }

    private ReportDO getExistingReport(Long userId, ReportTargetTypeEnum targetType, Long targetId) {
        List<ReportDO> exists = reportMapper.lambdaQuery()
            .eq(ReportDO::getReporterId, userId)
            .eq(ReportDO::getTargetType, targetType)
            .eq(ReportDO::getTargetId, targetId)
            .orderByDesc(ReportDO::getCreateTime)
            .list();
        return CollUtil.isEmpty(exists) ? null : exists.get(0);
    }

    private ReportDO createReport(ReportReq req,
                                  ReportTargetTypeEnum targetType,
                                  TargetRef target,
                                  Long userId,
                                  LocalDateTime now) {
        ReportDO report = new ReportDO();
        report.setReporterId(userId);
        report.setTargetType(targetType);
        report.setTargetId(req.getTargetId());
        report.setReasonType(StrUtil.trimToEmpty(req.getReasonType()).toUpperCase());
        report.setDescription(StrUtil.trimToNull(req.getDescription()));
        report.setMediaUrls(this.normalizeMediaUrls(req.getMediaUrls()));
        report.setStatus(ReportStatusEnum.PENDING);
        report.setHitCount(1);
        // C 端写入无后台登录态，审计字段显式落举报人
        report.setCreateUser(userId);
        report.setCreateTime(now);
        report.setUpdateUser(userId);
        report.setUpdateTime(now);
        reportMapper.insert(report);
        return report;
    }

    /**
     * 重复举报合并：仅累加次数并刷新补充材料，不新增记录
     *
     * @param existing 既有举报
     * @param req      请求参数
     * @param userId   举报人
     * @param now      当前时间
     * @return 合并后的举报实体
     */
    private ReportDO mergeReport(ReportDO existing, ReportReq req, Long userId, LocalDateTime now) {
        ReportDO update = new ReportDO();
        update.setId(existing.getId());
        update.setHitCount(Objects.requireNonNullElse(existing.getHitCount(), 1) + 1);
        update.setDescription(StrUtil.blankToDefault(StrUtil.trimToNull(req.getDescription()), existing
            .getDescription()));
        update.setUpdateUser(userId);
        update.setUpdateTime(now);
        reportMapper.updateById(update);
        existing.setHitCount(update.getHitCount());
        existing.setDescription(update.getDescription());
        if (!StrUtil.equalsIgnoreCase(existing.getReasonType(), StrUtil.trimToEmpty(req.getReasonType()))) {
            log.info("同一目标变更举报理由，按首次理由留存：举报 ID {}，原理由 {}，新理由 {}", existing.getId(), existing.getReasonType(), req
                .getReasonType());
        }
        return existing;
    }

    /**
     * 达阈值时提升被举报档案的审核优先级
     *
     * @param report 举报实体
     * @param target 目标引用
     */
    private void surfaceToQueue(ReportDO report, TargetRef target) {
        int hitCount = Objects.requireNonNullElse(report.getHitCount(), 1);
        if (hitCount != ModerationConstants.REPORT_HIT_THRESHOLD) {
            return;
        }
        if (target.caseId() != null) {
            findCaseMapper.lambdaUpdate()
                .setSql("report_count = report_count + 1")
                .eq(FindCaseDO::getId, target.caseId())
                .update();
        }
        log.warn("举报达阈值：{}#{} 累计有效举报 {} 次，档案 [{}] 已抬升 report_count，应进入审核队列顶部并下调广场排序权重", report.getTargetType(), report
            .getTargetId(), hitCount, target.caseId());
    }

    private List<String> normalizeMediaUrls(List<String> mediaUrls) {
        if (CollUtil.isEmpty(mediaUrls)) {
            return new ArrayList<>();
        }
        List<String> normalized = mediaUrls.stream()
            .filter(StrUtil::isNotBlank)
            .map(StrUtil::trim)
            .distinct()
            .collect(Collectors.toList());
        ValidationUtils.throwIf(normalized
            .size() > ModerationConstants.MAX_REPORT_MEDIA_COUNT, "证据图片最多上传 {} 张", ModerationConstants.MAX_REPORT_MEDIA_COUNT);
        return normalized;
    }

    private List<ReportResp> toRespList(List<ReportDO> reports) {
        if (CollUtil.isEmpty(reports)) {
            return new ArrayList<>();
        }
        Map<String, String> nameMap = this.groupTargetNames(reports);
        return reports.stream()
            .map(report -> this.toResp(report, nameMap.get(this.targetKey(report))))
            .collect(Collectors.toList());
    }

    private ReportResp toResp(ReportDO report, String targetName) {
        ReportResp resp = new ReportResp();
        resp.setId(report.getId());
        resp.setTargetType(report.getTargetType());
        resp.setTargetId(report.getTargetId());
        resp.setTargetName(targetName);
        resp.setReasonType(report.getReasonType());
        resp.setDescription(report.getDescription());
        resp.setMediaUrls(CollUtil.emptyIfNull(report.getMediaUrls()));
        resp.setStatus(report.getStatus());
        resp.setHitCount(report.getHitCount());
        resp.setHandleRemark(report.getHandleRemark());
        resp.setCreateTime(report.getCreateTime());
        return resp;
    }

    /**
     * 批量回查目标展示名（按类型分组，最多三次查询）
     *
     * @param reports 举报列表
     * @return 目标键 → 展示名
     */
    private Map<String, String> groupTargetNames(List<ReportDO> reports) {
        Map<String, String> nameMap = new HashMap<>(reports.size());
        this.fillCaseNames(reports, nameMap);
        this.fillLeadNames(reports, nameMap);
        this.fillUserNames(reports, nameMap);
        return nameMap;
    }

    private void fillCaseNames(List<ReportDO> reports, Map<String, String> nameMap) {
        List<Long> ids = this.targetIds(reports, ReportTargetTypeEnum.CASE);
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        findCaseMapper.selectList(Wrappers.<FindCaseDO>lambdaQuery().in(FindCaseDO::getId, ids))
            .forEach(item -> nameMap.put(this.targetKey(ReportTargetTypeEnum.CASE, item.getId()), StrUtil
                .blankToDefault(item.getName(), "寻人档案")));
    }

    private void fillLeadNames(List<ReportDO> reports, Map<String, String> nameMap) {
        List<Long> ids = this.targetIds(reports, ReportTargetTypeEnum.LEAD);
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        leadMapper.selectList(Wrappers.<LeadDO>lambdaQuery().in(LeadDO::getId, ids))
            .forEach(item -> nameMap.put(this.targetKey(ReportTargetTypeEnum.LEAD, item.getId()), StrUtil
                .maxLength(StrUtil.emptyIfNull(item.getDescription()), TARGET_SUMMARY_LENGTH)));
    }

    private void fillUserNames(List<ReportDO> reports, Map<String, String> nameMap) {
        List<Long> ids = this.targetIds(reports, ReportTargetTypeEnum.USER);
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        appUserMapper.selectList(Wrappers.<AppUserDO>lambdaQuery().in(AppUserDO::getId, ids))
            .forEach(item -> nameMap.put(this.targetKey(ReportTargetTypeEnum.USER, item.getId()), StrUtil
                .blankToDefault(item.getNickname(), "用户")));
    }

    private List<Long> targetIds(List<ReportDO> reports, ReportTargetTypeEnum targetType) {
        return reports.stream()
            .filter(report -> report.getTargetType() == targetType)
            .map(ReportDO::getTargetId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
    }

    private String targetKey(ReportDO report) {
        return this.targetKey(report.getTargetType(), report.getTargetId());
    }

    private String targetKey(ReportTargetTypeEnum targetType, Long targetId) {
        return targetType == null ? StringConstants.EMPTY : targetType.name() + ":" + targetId;
    }

    /**
     * 举报目标引用
     *
     * @param caseId 所属档案 ID（目标为用户时为 null）
     * @param name   展示名
     */
    private record TargetRef(Long caseId, String name) {
    }
}
