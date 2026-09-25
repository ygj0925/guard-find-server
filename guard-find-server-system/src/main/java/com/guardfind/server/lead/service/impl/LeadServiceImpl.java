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

package com.guardfind.server.lead.service.impl;

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
import com.guardfind.server.findcase.support.CaseConstants;
import com.guardfind.server.lead.constant.LeadConstants;
import com.guardfind.server.lead.enums.LeadReplyFromEnum;
import com.guardfind.server.lead.enums.LeadStatusEnum;
import com.guardfind.server.lead.mapper.LeadMapper;
import com.guardfind.server.lead.mapper.LeadReplyMapper;
import com.guardfind.server.lead.model.entity.LeadDO;
import com.guardfind.server.lead.model.entity.LeadReplyDO;
import com.guardfind.server.lead.model.query.LeadQuery;
import com.guardfind.server.lead.model.req.LeadReplyReq;
import com.guardfind.server.lead.model.req.LeadReq;
import com.guardfind.server.lead.model.req.LeadStatusReq;
import com.guardfind.server.lead.model.resp.LeadPageResp;
import com.guardfind.server.lead.model.resp.LeadReplyResp;
import com.guardfind.server.lead.model.resp.LeadResp;
import com.guardfind.server.lead.service.LeadService;
import com.guardfind.server.lead.support.LeadRateLimiter;
import com.guardfind.server.lead.support.LeadViewAssembler;
import com.guardfind.server.moderation.model.ContentCheckResult;
import com.guardfind.server.moderation.service.SensitiveWordMatcher;
import com.guardfind.server.notification.enums.NotificationEventEnum;
import com.guardfind.server.notification.service.AppMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.starter.core.util.ServletUtils;
import top.continew.starter.core.util.validation.CheckUtils;
import top.continew.starter.core.util.validation.ValidationUtils;
import top.continew.starter.extension.crud.model.query.PageQuery;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 线索业务实现
 *
 * <p>
 * 提交链路的闸门顺序是刻意的：登录与绑机（身份可追溯）→ 档案状态（不接收不可提交对象的线索）→ 字段格式 →
 * 内容风控（违法/辱骂直接拒，诈骗话术转高风险）→ 限频计数（被前面任一环节拒掉的请求不消耗额度）→ 落库。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeadServiceImpl implements LeadService {

    /**
     * 站内信摘要长度（超出截断，避免整段线索描述进通知）
     */
    private static final int NOTICE_SUMMARY_LENGTH = 60;

    private final LeadMapper leadMapper;
    private final LeadReplyMapper leadReplyMapper;
    private final FindCaseMapper findCaseMapper;
    private final AppUserMapper appUserMapper;
    private final AppMessageService appMessageService;
    private final SensitiveWordMatcher sensitiveWordMatcher;
    private final LeadRateLimiter leadRateLimiter;
    private final LeadViewAssembler leadViewAssembler;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeadResp submit(LeadReq req, Long caseId) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        this.checkPhoneBound(userId);
        FindCaseDO caseDO = this.getPublishedCase(caseId);
        ValidationUtils.throwIfNull(req, "请填写线索内容");
        String description = StrUtil.trim(req.getDescription());
        ValidationUtils.throwIf(StrUtil.length(description) < LeadConstants.MIN_DESCRIPTION_LENGTH || StrUtil
            .length(description) > LeadConstants.MAX_DESCRIPTION_LENGTH, "线索描述长度必须在 {}-{} 个字符之间", LeadConstants.MIN_DESCRIPTION_LENGTH, LeadConstants.MAX_DESCRIPTION_LENGTH);
        List<String> mediaUrls = this.normalizeMediaUrls(req.getMediaUrls());
        String contactMode = this.resolveContactMode(req.getContactMode());
        String contactValue = this.resolveContactValue(contactMode, req.getContactValue());
        ValidationUtils.throwIf(req.getOccurTime() != null && req.getOccurTime()
            .isAfter(LocalDateTime.now()), "发现时间不能晚于当前时间");
        // 内容风控：违法/辱骂直接拒绝，诈骗话术放行但打高风险标记交人工核实
        ContentCheckResult checkResult = sensitiveWordMatcher.match(this.buildCheckText(description, req
            .getOccurAddress()));
        ValidationUtils.throwIf(checkResult.isRejected(), "线索内容不合规：{}", checkResult.getReason());
        boolean highRisk = checkResult.isHighRisk();
        // 反滥用：同用户同档案 1h/3 条、同 IP 1h/10 条（业务层计数，不依赖注解）
        String ip = this.clientIp();
        this.leadRateLimiter.acquire(userId, caseId, ip);
        LocalDateTime now = LocalDateTime.now();
        LeadDO lead = new LeadDO();
        lead.setCaseId(caseId);
        lead.setSubmitterId(userId);
        lead.setOccurTime(req.getOccurTime());
        lead.setOccurAddress(StrUtil.trimToNull(req.getOccurAddress()));
        lead.setDescription(description);
        lead.setMediaUrls(mediaUrls);
        lead.setContactMode(contactMode);
        lead.setContactValue(contactValue);
        lead.setStatus(LeadStatusEnum.PENDING);
        lead.setHighRisk(highRisk);
        lead.setIp(ip);
        // C 端写入无后台登录态，审计字段显式落提交人
        lead.setCreateUser(userId);
        lead.setCreateTime(now);
        lead.setUpdateUser(userId);
        lead.setUpdateTime(now);
        leadMapper.insert(lead);
        this.increaseLeadCount(caseId);
        this.notifyOwner(caseDO, highRisk);
        // 提交人自身也不返回明文（契约：联系方式仅档案所有人可见明文）
        return leadViewAssembler.toResp(lead, false);
    }

    @Override
    public LeadPageResp pageByCase(Long caseId, PageQuery pageQuery) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        FindCaseDO caseDO = findCaseMapper.selectById(caseId);
        CheckUtils.throwIfNull(caseDO, "该信息不存在或已下架");
        boolean isOwner = Objects.equals(caseDO.getOwnerId(), userId);
        CheckUtils.throwIf(!isOwner && !AppUserContextHolder.isVolunteer(), "仅档案发布人与已认证志愿者可查看该档案的线索");
        LambdaQueryWrapper<LeadDO> wrapper = Wrappers.<LeadDO>lambdaQuery()
            .eq(LeadDO::getCaseId, caseId)
            .orderByDesc(LeadDO::getCreateTime)
            .orderByDesc(LeadDO::getId);
        Page<LeadDO> page = leadMapper.selectPage(new Page<>(this.page(pageQuery), this.size(pageQuery)), wrapper);
        return LeadPageResp.of(leadViewAssembler.toRespList(page.getRecords(), isOwner), page.getTotal());
    }

    @Override
    public LeadPageResp mine(LeadQuery query, PageQuery pageQuery) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        LeadStatusEnum status = query == null ? null : query.getStatus();
        LambdaQueryWrapper<LeadDO> wrapper = Wrappers.<LeadDO>lambdaQuery()
            .eq(LeadDO::getSubmitterId, userId)
            .eq(status != null, LeadDO::getStatus, status)
            .orderByDesc(LeadDO::getCreateTime)
            .orderByDesc(LeadDO::getId);
        Page<LeadDO> page = leadMapper.selectPage(new Page<>(this.page(pageQuery), this.size(pageQuery)), wrapper);
        // 我提交的线索：除本人即档案发布人的场景外，联系方式一律掩码返回
        return LeadPageResp.of(leadViewAssembler.toRespList(page.getRecords(), false), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, LeadStatusReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        LeadDO lead = this.getLead(id);
        FindCaseDO caseDO = this.getCase(lead.getCaseId());
        CheckUtils.throwIf(!Objects.equals(caseDO.getOwnerId(), userId), "仅档案发布人可处置自己档案的线索");
        ValidationUtils.throwIfNull(req, "请填写处置结果");
        LeadStatusEnum target = LeadStatusEnum.of(req.getStatus());
        ValidationUtils.throwIfNull(target, "线索状态取值无效");
        CheckUtils.throwIf(!LeadStatusEnum.actionable().contains(target), "线索状态仅支持 ACCEPTED/REJECTED/CONTACTED");
        LocalDateTime now = LocalDateTime.now();
        LeadDO update = new LeadDO();
        update.setId(id);
        update.setStatus(target);
        update.setHandleRemark(StrUtil.trimToNull(req.getRemark()));
        update.setUpdateUser(userId);
        update.setUpdateTime(now);
        leadMapper.updateById(update);
        this.notifySubmitter(lead, NotificationEventEnum.LEAD_REPLIED, "线索已%s".formatted(target
            .getDescription()), "您为「%s」提供的线索已被家属标记为「%s」%s。平台提醒：任何以找回为由要求先转钱、交核实费的行为都是诈骗。".formatted(this
                .caseName(caseDO), target.getDescription(), this.remarkSuffix(req.getRemark())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeadReplyResp reply(Long id, LeadReplyReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        LeadDO lead = this.getLead(id);
        FindCaseDO caseDO = this.getCase(lead.getCaseId());
        CheckUtils.throwIf(!Objects.equals(caseDO.getOwnerId(), userId), "仅档案发布人可回复自己档案的线索");
        ValidationUtils.throwIfNull(req, "请填写回复内容");
        String content = StrUtil.trim(req.getContent());
        ValidationUtils.throwIf(StrUtil.length(content) < 1 || StrUtil
            .length(content) > LeadConstants.MAX_REPLY_LENGTH, "回复内容长度必须在 1-{} 个字符之间", LeadConstants.MAX_REPLY_LENGTH);
        ContentCheckResult checkResult = sensitiveWordMatcher.match(content);
        ValidationUtils.throwIf(checkResult.isRejected(), "回复内容不合规：{}", checkResult.getReason());
        LocalDateTime now = LocalDateTime.now();
        LeadReplyDO reply = new LeadReplyDO();
        reply.setLeadId(id);
        reply.setFromSide(LeadReplyFromEnum.OWNER);
        reply.setFromUserId(userId);
        reply.setContent(content);
        reply.setCreateUser(userId);
        reply.setCreateTime(now);
        reply.setUpdateUser(userId);
        reply.setUpdateTime(now);
        leadReplyMapper.insert(reply);
        this.notifySubmitter(lead, NotificationEventEnum.LEAD_REPLIED, "家属回复了您的线索", "您为「%s」提供的线索收到家属回复：%s。请继续配合提供更多信息。"
            .formatted(this.caseName(caseDO), StrUtil.maxLength(content, NOTICE_SUMMARY_LENGTH)));
        return LeadReplyResp.of(reply.getId(), reply.getContent(), reply.getFromSide(), reply.getCreateTime());
    }

    /**
     * 校验提交人已绑定手机号（线索必须可回溯到真实联系人）
     *
     * @param userId App 用户 ID
     */
    private void checkPhoneBound(Long userId) {
        AppUserDO user = appUserMapper.selectById(userId);
        CheckUtils.throwIfNull(user, "账号信息不存在，请重新登录");
        ValidationUtils.throwIf(StrUtil.isBlank(user.getPhone()), "请先在「我的资料」中绑定手机号，再提交线索");
    }

    /**
     * 取可提交线索的档案：仅已发布（PUBLISHED）档案开放线索入口
     *
     * @param caseId 档案 ID
     * @return 档案实体
     */
    private FindCaseDO getPublishedCase(Long caseId) {
        FindCaseDO caseDO = this.getCase(caseId);
        CheckUtils.throwIf(caseDO.getStatus() != CaseStatusEnum.PUBLISHED, "该档案当前为「{}」，暂不接受线索", this
            .statusDescription(caseDO.getStatus()));
        return caseDO;
    }

    private FindCaseDO getCase(Long caseId) {
        FindCaseDO caseDO = findCaseMapper.selectById(caseId);
        CheckUtils.throwIfNull(caseDO, "该信息不存在或已下架");
        return caseDO;
    }

    private LeadDO getLead(Long id) {
        LeadDO lead = leadMapper.selectById(id);
        CheckUtils.throwIfNull(lead, "线索不存在或已删除");
        return lead;
    }

    /**
     * 联络方式：缺省 IN_APP（契约默认值）
     *
     * @param contactMode 入参
     * @return 归一后的联络方式
     */
    private String resolveContactMode(String contactMode) {
        String mode = StrUtil.blankToDefault(StrUtil.trim(contactMode), CaseConstants.CONTACT_MODE_IN_APP)
            .toUpperCase();
        ValidationUtils.throwIf(!CaseConstants.CONTACT_MODE_IN_APP.equals(mode) && !CaseConstants.CONTACT_MODE_PHONE
            .equals(mode), "联络方式仅支持 IN_APP/PHONE");
        return mode;
    }

    /**
     * 联系方式：PHONE 必填、IN_APP 一律不落库（站内回复已足够）
     *
     * @param contactMode  联络方式
     * @param contactValue 入参
     * @return 待加密落库的联系方式
     */
    private String resolveContactValue(String contactMode, String contactValue) {
        String value = StrUtil.trimToNull(contactValue);
        if (CaseConstants.CONTACT_MODE_PHONE.equals(contactMode)) {
            ValidationUtils.throwIfBlank(value, "联络方式为 PHONE 时，联系方式不能为空");
        }
        return CaseConstants.CONTACT_MODE_PHONE.equals(contactMode) ? value : null;
    }

    /**
     * 图片列表归一：去空白、去重、限 3 张
     *
     * @param mediaUrls 入参
     * @return 图片列表
     */
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
            .size() > LeadConstants.MAX_MEDIA_COUNT, "线索图片最多上传 {} 张", LeadConstants.MAX_MEDIA_COUNT);
        return normalized;
    }

    /**
     * 待风控文本：描述 + 发现地点（拆字规避在归一化阶段仍会命中）
     *
     * @param description  描述
     * @param occurAddress 发现地点
     * @return 拼接文本
     */
    private String buildCheckText(String description, String occurAddress) {
        return StrUtil.join(StrUtil.SPACE, StrUtil.emptyIfNull(description), StrUtil.emptyIfNull(occurAddress));
    }

    private void increaseLeadCount(Long caseId) {
        findCaseMapper.lambdaUpdate().setSql("lead_count = lead_count + 1").eq(FindCaseDO::getId, caseId).update();
    }

    /**
     * 新线索通知档案发布人（高风险线索在文案中直接提示"待核实"，避免家属把诈骗当线索去联系）
     *
     * @param caseDO   档案
     * @param highRisk 是否高风险
     */
    private void notifyOwner(FindCaseDO caseDO, boolean highRisk) {
        Long ownerId = caseDO.getOwnerId();
        if (ownerId == null) {
            log.warn("档案 [{}] 无发布人（采集来源），跳过线索站内信", caseDO.getId());
            return;
        }
        String caseName = this.caseName(caseDO);
        String content = highRisk
            ? "您发布的寻人档案「%s」收到 1 条待核实线索，疑似含诈骗话术，平台已转人工核实。请切勿先行转账或支付任何「核实费」「邮费」。".formatted(caseName)
            : "您发布的寻人档案「%s」收到来自好心人的 1 条新线索，可在「我的发布 - 线索」中查看并回复。".formatted(caseName);
        appMessageService.send(ownerId, NotificationEventEnum.CASE_LEAD, CaseConstants.MESSAGE_BIZ_TYPE, caseDO
            .getId(), "收到 1 条新线索", content);
    }

    private void notifySubmitter(LeadDO lead, NotificationEventEnum event, String title, String content) {
        if (lead.getSubmitterId() == null) {
            return;
        }
        appMessageService.send(lead.getSubmitterId(), event, LeadConstants.MESSAGE_BIZ_TYPE, lead
            .getId(), title, content);
    }

    private String remarkSuffix(String remark) {
        return StrUtil.isBlank(remark)
            ? StrUtil.EMPTY
            : "，说明：%s".formatted(StrUtil.maxLength(remark, NOTICE_SUMMARY_LENGTH));
    }

    private String caseName(FindCaseDO caseDO) {
        return StrUtil.blankToDefault(caseDO.getName(), "寻人档案");
    }

    private String statusDescription(CaseStatusEnum status) {
        return status == null ? "未知" : status.getDescription();
    }

    private int page(PageQuery pageQuery) {
        return pageQuery == null || pageQuery.getPage() == null ? 1 : pageQuery.getPage();
    }

    private int size(PageQuery pageQuery) {
        return pageQuery == null || pageQuery.getSize() == null ? 10 : pageQuery.getSize();
    }

    /**
     * 客户端 IP（无请求上下文时返回 null，由限频器降级跳过 IP 维度）
     *
     * @return IP
     */
    private String clientIp() {
        try {
            return ServletUtils.getRequestIp();
        } catch (Exception e) {
            return null;
        }
    }
}
