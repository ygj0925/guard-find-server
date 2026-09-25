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

package com.guardfind.server.findcase.converter;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.guardfind.server.findcase.enums.AgeGroupEnum;
import com.guardfind.server.findcase.enums.CaseOperatorTypeEnum;
import com.guardfind.server.findcase.model.entity.CaseStatusLogDO;
import com.guardfind.server.findcase.model.entity.FindCaseAliasDO;
import com.guardfind.server.findcase.model.entity.FindCaseDO;
import com.guardfind.server.findcase.model.entity.FindCaseMediaDO;
import com.guardfind.server.findcase.model.resp.CaseDetailResp;
import com.guardfind.server.findcase.model.resp.CasePublicResp;
import com.guardfind.server.findcase.model.resp.CaseStatusHistoryResp;
import top.continew.starter.core.constant.StringConstants;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 档案视图转换器（脱敏的唯一出口）
 *
 * <p>
 * 公开视图只允许经过 {@link #toPublic(FindCaseDO, List, List, Function)} 产出：详细地址、联系电话、精确到日的
 * 失踪时间在这里被物理丢弃，而不是靠"序列化时打码"，避免任何一处忘记加注解就泄露。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public final class CaseViewConverter {

    /**
     * 失踪年月格式（公开视图只到月）
     */
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * 视频媒体类型（封面优先取图片）
     */
    private static final String MEDIA_TYPE_VIDEO = "VIDEO";

    private CaseViewConverter() {
    }

    /**
     * 转换为公开视图（脱敏）
     *
     * @param caseDO             档案实体
     * @param mediaList          媒体列表（可为空）
     * @param aliasList          别名列表（可为空）
     * @param regionNameResolver 区划编码 → 名称解析器
     * @return 公开视图
     */
    public static CasePublicResp toPublic(FindCaseDO caseDO,
                                          List<FindCaseMediaDO> mediaList,
                                          List<FindCaseAliasDO> aliasList,
                                          Function<String, String> regionNameResolver) {
        CasePublicResp resp = new CasePublicResp();
        fillPublic(resp, caseDO, mediaList, aliasList, regionNameResolver);
        return resp;
    }

    /**
     * 转换为完整视图（含敏感字段与状态轨迹）
     *
     * @param caseDO             档案实体
     * @param mediaList          媒体列表（可为空）
     * @param aliasList          别名列表（可为空）
     * @param statusLogList      状态轨迹（可为空）
     * @param operatorNames      操作人 ID → 昵称
     * @param regionNameResolver 区划编码 → 名称解析器
     * @return 完整视图
     */
    public static CaseDetailResp toDetail(FindCaseDO caseDO,
                                          List<FindCaseMediaDO> mediaList,
                                          List<FindCaseAliasDO> aliasList,
                                          List<CaseStatusLogDO> statusLogList,
                                          Map<Long, String> operatorNames,
                                          Function<String, String> regionNameResolver) {
        CaseDetailResp resp = new CaseDetailResp();
        fillPublic(resp, caseDO, mediaList, aliasList, regionNameResolver);
        resp.setAddress(caseDO.getAddress());
        resp.setMissingTime(caseDO.getMissingTime());
        resp.setBirthday(caseDO.getBirthday());
        resp.setHometownText(joinNotBlank(regionNameResolver.apply(caseDO.getHometownCode())));
        resp.setBodyType(caseDO.getBodyType());
        resp.setHairType(caseDO.getHairType());
        resp.setAccent(caseDO.getAccent());
        resp.setClothing(caseDO.getClothing());
        resp.setBelongings(caseDO.getBelongings());
        resp.setNarrative(caseDO.getNarrative());
        resp.setFamilyClues(caseDO.getFamilyClues());
        resp.setOtherNote(caseDO.getOtherNote());
        resp.setPoliceReported(caseDO.getPoliceReported());
        resp.setPoliceStation(caseDO.getPoliceStation());
        resp.setCaseNo(caseDO.getCaseAcceptedNo());
        resp.setBloodSampleStatus(caseDO.getBloodSampleStatus());
        resp.setFollowVolunteer(caseDO.getFollowVolunteer());
        resp.setContactMode(caseDO.getContactMode());
        resp.setContactPhone(caseDO.getContactPhone());
        resp.setRelationType(caseDO.getRelationType());
        resp.setVersion(caseDO.getVersion());
        resp.setAuditRemark(caseDO.getAuditRemark());
        resp.setFoundTime(caseDO.getFoundTime());
        resp.setOwnerMasked(false);
        resp.setStatusHistory(toStatusHistory(statusLogList, operatorNames));
        return resp;
    }

    private static void fillPublic(CasePublicResp resp,
                                   FindCaseDO caseDO,
                                   List<FindCaseMediaDO> mediaList,
                                   List<FindCaseAliasDO> aliasList,
                                   Function<String, String> regionNameResolver) {
        resp.setId(caseDO.getId());
        resp.setName(caseDO.getName());
        resp.setGender(caseDO.getGender() == null ? null : caseDO.getGender().name());
        resp.setAgeAtMissing(caseDO.getAgeAtMissing());
        resp.setAgeGroup(resolveAgeGroup(caseDO));
        resp.setAliases(toAliasNames(aliasList));
        resp.setMissingMonth(caseDO.getMissingTime() == null ? null : caseDO.getMissingTime().format(MONTH_FORMATTER));
        // 区划文本：省 + 市，公开视图不下钻到区县名称之外的信息
        resp.setRegionText(joinNotBlank(regionNameResolver.apply(caseDO.getProvinceCode()), regionNameResolver
            .apply(caseDO.getCityCode())));
        resp.setDistrictText(regionNameResolver.apply(caseDO.getDistrictCode()));
        resp.setHeight(caseDO.getHeight());
        resp.setFeatures(caseDO.getFeatures());
        resp.setCaseType(caseDO.getCaseType());
        resp.setUrgencyLevel(caseDO.getUrgencyLevel());
        resp.setMissingHours(toMissingHours(caseDO.getMissingTime()));
        resp.setStatus(caseDO.getStatus());
        List<FindCaseMediaDO> sortedMedia = sortMedia(mediaList);
        resp.setCoverUrl(toCoverUrl(sortedMedia));
        resp.setMediaUrls(toMediaUrls(sortedMedia));
        resp.setLeadCount(Objects.requireNonNullElse(caseDO.getLeadCount(), 0));
        resp.setSourceType(caseDO.getSourceType());
        resp.setSourceUrl(caseDO.getSourceUrl());
        resp.setPublishTime(caseDO.getPublishTime());
        resp.setOwnerMasked(true);
    }

    /**
     * 状态轨迹转换
     *
     * @param statusLogList 状态日志
     * @param operatorNames 操作人昵称索引
     * @return 轨迹列表
     */
    public static List<CaseStatusHistoryResp> toStatusHistory(List<CaseStatusLogDO> statusLogList,
                                                              Map<Long, String> operatorNames) {
        if (CollUtil.isEmpty(statusLogList)) {
            return new ArrayList<>();
        }
        return statusLogList.stream().map(log -> {
            CaseStatusHistoryResp history = new CaseStatusHistoryResp();
            history.setFrom(log.getFromStatus());
            history.setTo(log.getToStatus());
            history.setReason(StrUtil.blankToDefault(log.getReason(), StrUtil.blankToDefault(log.getReasonCode(), log
                .getToStatus() == null ? null : log.getToStatus().getDescription())));
            history.setTime(log.getCreateTime());
            history.setOperator(resolveOperator(log, operatorNames));
            return history;
        }).collect(Collectors.toList());
    }

    private static String resolveOperator(CaseStatusLogDO log, Map<Long, String> operatorNames) {
        CaseOperatorTypeEnum operatorType = CaseOperatorTypeEnum.of(log.getOperatorType());
        if (operatorType == CaseOperatorTypeEnum.SYSTEM) {
            return CaseOperatorTypeEnum.SYSTEM.getDescription();
        }
        String nickname = log.getOperatorId() == null ? null : operatorNames.get(log.getOperatorId());
        return StrUtil.blankToDefault(nickname, operatorType.getDescription());
    }

    private static String resolveAgeGroup(FindCaseDO caseDO) {
        if (StrUtil.isNotBlank(caseDO.getAgeGroup())) {
            return caseDO.getAgeGroup();
        }
        AgeGroupEnum ageGroup = AgeGroupEnum.of(caseDO.getAgeAtMissing());
        return ageGroup == null ? null : ageGroup.getValue();
    }

    private static Long toMissingHours(LocalDateTime missingTime) {
        if (missingTime == null) {
            return null;
        }
        long hours = Duration.between(missingTime, LocalDateTime.now()).toHours();
        return Math.max(hours, 0L);
    }

    private static List<FindCaseMediaDO> sortMedia(List<FindCaseMediaDO> mediaList) {
        if (CollUtil.isEmpty(mediaList)) {
            return new ArrayList<>();
        }
        return mediaList.stream()
            .filter(media -> StrUtil.isNotBlank(media.getUrl()))
            .sorted(Comparator.comparing((FindCaseMediaDO media) -> Objects.requireNonNullElse(media.getSort(), 0))
                .thenComparing(FindCaseMediaDO::getId))
            .collect(Collectors.toList());
    }

    private static String toCoverUrl(List<FindCaseMediaDO> sortedMedia) {
        if (CollUtil.isEmpty(sortedMedia)) {
            return null;
        }
        FindCaseMediaDO cover = sortedMedia.stream()
            .filter(media -> !StrUtil.equals(media.getType(), MEDIA_TYPE_VIDEO))
            .findFirst()
            .orElse(sortedMedia.get(0));
        return StrUtil.blankToDefault(cover.getThumbUrl(), cover.getUrl());
    }

    private static List<String> toMediaUrls(List<FindCaseMediaDO> sortedMedia) {
        return sortedMedia.stream().map(FindCaseMediaDO::getUrl).collect(Collectors.toList());
    }

    private static List<String> toAliasNames(List<FindCaseAliasDO> aliasList) {
        if (CollUtil.isEmpty(aliasList)) {
            return new ArrayList<>();
        }
        return aliasList.stream()
            .map(FindCaseAliasDO::getName)
            .filter(StrUtil::isNotBlank)
            .distinct()
            .collect(Collectors.toList());
    }

    private static String joinNotBlank(String... values) {
        if (values == null) {
            return StrUtil.EMPTY;
        }
        StringJoiner joiner = new StringJoiner(StringConstants.SPACE);
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                joiner.add(value.trim());
            }
        }
        return joiner.toString();
    }
}
