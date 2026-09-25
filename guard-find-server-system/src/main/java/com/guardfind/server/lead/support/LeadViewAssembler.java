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

package com.guardfind.server.lead.support;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.DesensitizedUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.findcase.mapper.FindCaseMapper;
import com.guardfind.server.findcase.model.entity.FindCaseDO;
import com.guardfind.server.lead.mapper.LeadReplyMapper;
import com.guardfind.server.lead.model.entity.LeadDO;
import com.guardfind.server.lead.model.entity.LeadReplyDO;
import com.guardfind.server.lead.model.resp.LeadReplyResp;
import com.guardfind.server.lead.model.resp.LeadResp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.continew.starter.core.util.CollUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 线索视图装配器
 *
 * <p>
 * "实体 → 视图"的唯一出口：回复、提交人昵称、档案姓名均按页批量回查（避免 N+1），
 * 联系方式的明文/掩码判定也集中在此，避免脱敏口径散落到各接口。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Component
@RequiredArgsConstructor
public class LeadViewAssembler {

    /**
     * 掩码占位符
     */
    private static final String MASK = "*";

    /**
     * 提交人昵称末尾掩码位数
     */
    private static final int NICKNAME_MASK_LENGTH = 2;

    /**
     * 提交人缺省昵称（账号已注销或昵称为空时）
     */
    private static final String DEFAULT_SUBMITTER_LABEL = "好心人";

    private final LeadReplyMapper leadReplyMapper;
    private final AppUserMapper appUserMapper;
    private final FindCaseMapper findCaseMapper;

    /**
     * 批量装配线索视图
     *
     * @param leadList      线索实体列表
     * @param exposeContact 是否返回联系方式明文（仅档案发布人为 true）
     * @return 线索视图列表（顺序与入参一致）
     */
    public List<LeadResp> toRespList(List<LeadDO> leadList, boolean exposeContact) {
        if (CollUtil.isEmpty(leadList)) {
            return new ArrayList<>();
        }
        List<Long> leadIds = CollUtils.mapToList(leadList, LeadDO::getId);
        List<Long> submitterIds = leadList.stream()
            .map(LeadDO::getSubmitterId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        List<Long> caseIds = leadList.stream()
            .map(LeadDO::getCaseId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, List<LeadReplyDO>> replyMap = this.groupReplies(leadIds);
        Map<Long, String> nicknameMap = this.groupNicknames(submitterIds);
        Map<Long, String> caseNameMap = this.groupCaseNames(caseIds);
        return leadList.stream()
            .map(lead -> this.toResp(lead, exposeContact, replyMap.get(lead.getId()), nicknameMap.get(lead
                .getSubmitterId()), caseNameMap.get(lead.getCaseId())))
            .collect(Collectors.toList());
    }

    /**
     * 装配单条线索视图
     *
     * @param lead          线索实体
     * @param exposeContact 是否返回联系方式明文
     * @return 线索视图
     */
    public LeadResp toResp(LeadDO lead, boolean exposeContact) {
        return this.toResp(lead, exposeContact, this.listReplies(lead.getId()), this.nicknameOf(lead
            .getSubmitterId()), this.caseNameOf(lead.getCaseId()));
    }

    private LeadResp toResp(LeadDO lead,
                            boolean exposeContact,
                            List<LeadReplyDO> replies,
                            String submitterNickname,
                            String caseName) {
        LeadResp resp = new LeadResp();
        resp.setId(lead.getId());
        resp.setCaseId(lead.getCaseId());
        resp.setCaseName(caseName);
        resp.setOccurTime(lead.getOccurTime());
        resp.setOccurAddress(lead.getOccurAddress());
        resp.setDescription(lead.getDescription());
        resp.setMediaUrls(CollUtil.emptyIfNull(lead.getMediaUrls()));
        resp.setContactMode(lead.getContactMode());
        resp.setContactValue(exposeContact ? lead.getContactValue() : this.maskContact(lead.getContactValue()));
        resp.setSubmitterMasked(this.maskNickname(submitterNickname));
        resp.setStatus(lead.getStatus());
        resp.setHighRisk(Boolean.TRUE.equals(lead.getHighRisk()));
        resp.setHandleRemark(lead.getHandleRemark());
        resp.setReplies(CollUtil.emptyIfNull(replies)
            .stream()
            .map(reply -> LeadReplyResp.of(reply.getId(), reply.getContent(), reply.getFromSide(), reply
                .getCreateTime()))
            .collect(Collectors.toList()));
        resp.setCreateTime(lead.getCreateTime());
        return resp;
    }

    private List<LeadReplyDO> listReplies(Long leadId) {
        if (leadId == null) {
            return new ArrayList<>();
        }
        return leadReplyMapper.lambdaQuery()
            .eq(LeadReplyDO::getLeadId, leadId)
            .orderByAsc(LeadReplyDO::getCreateTime)
            .orderByAsc(LeadReplyDO::getId)
            .list();
    }

    private Map<Long, List<LeadReplyDO>> groupReplies(List<Long> leadIds) {
        if (CollUtil.isEmpty(leadIds)) {
            return Collections.emptyMap();
        }
        return leadReplyMapper.lambdaQuery()
            .in(LeadReplyDO::getLeadId, leadIds)
            .orderByAsc(LeadReplyDO::getCreateTime)
            .orderByAsc(LeadReplyDO::getId)
            .list()
            .stream()
            .collect(Collectors.groupingBy(LeadReplyDO::getLeadId));
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

    private String nicknameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        AppUserDO user = appUserMapper.selectById(userId);
        return user == null ? null : user.getNickname();
    }

    private Map<Long, String> groupCaseNames(List<Long> caseIds) {
        if (CollUtil.isEmpty(caseIds)) {
            return Collections.emptyMap();
        }
        return findCaseMapper.selectList(Wrappers.<FindCaseDO>lambdaQuery().in(FindCaseDO::getId, caseIds))
            .stream()
            .collect(Collectors.toMap(FindCaseDO::getId, item -> StrUtil.emptyIfNull(item.getName()), (existing,
                                                                                                       replacement) -> existing));
    }

    private String caseNameOf(Long caseId) {
        if (caseId == null) {
            return null;
        }
        FindCaseDO caseDO = findCaseMapper.selectById(caseId);
        return caseDO == null ? null : caseDO.getName();
    }

    /**
     * 提交人昵称掩码：保留前缀，末尾若干位以 * 替换（例：无脚星友4821 → 无脚星友48**）
     *
     * @param nickname 昵称
     * @return 掩码昵称
     */
    private String maskNickname(String nickname) {
        if (StrUtil.isBlank(nickname)) {
            return DEFAULT_SUBMITTER_LABEL;
        }
        int length = nickname.length();
        int keep = Math.max(0, length - NICKNAME_MASK_LENGTH);
        return keep == 0
            ? StrUtil.repeat(MASK, length)
            : nickname.substring(0, keep) + StrUtil.repeat(MASK, length - keep);
    }

    /**
     * 联系方式掩码：手机号按运营商通用 3+4 段掩码，其余保留前 2 位
     *
     * @param contactValue 联系方式明文
     * @return 掩码值，入参为空时返回 null
     */
    private String maskContact(String contactValue) {
        if (StrUtil.isBlank(contactValue)) {
            return null;
        }
        if (Validator.isMobile(contactValue)) {
            return DesensitizedUtil.mobilePhone(contactValue);
        }
        int length = contactValue.length();
        return length <= 2
            ? StrUtil.repeat(MASK, length)
            : contactValue.substring(0, 2) + StrUtil.repeat(MASK, Math.min(length - 2, 6));
    }
}
