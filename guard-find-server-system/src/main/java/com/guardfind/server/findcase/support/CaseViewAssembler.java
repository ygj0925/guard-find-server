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

package com.guardfind.server.findcase.support;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.findcase.converter.CaseViewConverter;
import com.guardfind.server.findcase.enums.CaseOperatorTypeEnum;
import com.guardfind.server.findcase.mapper.CaseStatusLogMapper;
import com.guardfind.server.findcase.mapper.FindCaseAliasMapper;
import com.guardfind.server.findcase.mapper.FindCaseMediaMapper;
import com.guardfind.server.findcase.model.entity.CaseStatusLogDO;
import com.guardfind.server.findcase.model.entity.FindCaseAliasDO;
import com.guardfind.server.findcase.model.entity.FindCaseDO;
import com.guardfind.server.findcase.model.entity.FindCaseMediaDO;
import com.guardfind.server.findcase.model.resp.CaseDetailResp;
import com.guardfind.server.findcase.model.resp.CasePublicResp;
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
 * 档案视图装配器
 *
 * <p>
 * 负责"实体 → 视图"的批量装配：一次批量取回媒体与别名，避免广场一页 10 条打出 30 条 SQL；
 * 所有对外视图都经此产出，脱敏口径只有一处（见 {@link CaseViewConverter}）。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Component
@RequiredArgsConstructor
public class CaseViewAssembler {

    /**
     * 完整视图内联的状态轨迹条数
     */
    private static final int STATUS_HISTORY_LIMIT = 20;

    private final FindCaseMediaMapper findCaseMediaMapper;
    private final FindCaseAliasMapper findCaseAliasMapper;
    private final CaseStatusLogMapper caseStatusLogMapper;
    private final AppUserMapper appUserMapper;
    private final RegionRepository regionRepository;

    /**
     * 批量装配公开视图
     *
     * @param caseList 档案实体列表
     * @return 公开视图列表（顺序与入参一致）
     */
    public List<CasePublicResp> toPublicList(List<FindCaseDO> caseList) {
        if (CollUtil.isEmpty(caseList)) {
            return new ArrayList<>();
        }
        List<Long> caseIds = CollUtils.mapToList(caseList, FindCaseDO::getId);
        Map<Long, List<FindCaseMediaDO>> mediaMap = this.groupMedia(caseIds);
        Map<Long, List<FindCaseAliasDO>> aliasMap = this.groupAliases(caseIds);
        return caseList.stream()
            .map(caseDO -> CaseViewConverter.toPublic(caseDO, mediaMap.get(caseDO.getId()), aliasMap.get(caseDO
                .getId()), regionRepository::nameOf))
            .collect(Collectors.toList());
    }

    /**
     * 装配单条公开视图
     *
     * @param caseDO 档案实体
     * @return 公开视图
     */
    public CasePublicResp toPublic(FindCaseDO caseDO) {
        return CaseViewConverter.toPublic(caseDO, this.listMedia(caseDO.getId()), this.listAliases(caseDO
            .getId()), regionRepository::nameOf);
    }

    /**
     * 批量装配完整视图（不含状态轨迹，列表场景够用）
     *
     * @param caseList 档案实体列表
     * @return 完整视图列表
     */
    public List<CaseDetailResp> toDetailList(List<FindCaseDO> caseList) {
        if (CollUtil.isEmpty(caseList)) {
            return new ArrayList<>();
        }
        List<Long> caseIds = CollUtils.mapToList(caseList, FindCaseDO::getId);
        Map<Long, List<FindCaseMediaDO>> mediaMap = this.groupMedia(caseIds);
        Map<Long, List<FindCaseAliasDO>> aliasMap = this.groupAliases(caseIds);
        return caseList.stream()
            .map(caseDO -> CaseViewConverter.toDetail(caseDO, mediaMap.get(caseDO.getId()), aliasMap.get(caseDO
                .getId()), null, Collections.emptyMap(), regionRepository::nameOf))
            .collect(Collectors.toList());
    }

    /**
     * 装配完整视图（含状态轨迹与操作人昵称）
     *
     * @param caseDO 档案实体
     * @return 完整视图
     */
    public CaseDetailResp toDetail(FindCaseDO caseDO) {
        List<CaseStatusLogDO> statusLogs = caseDO.getId() == null
            ? new ArrayList<>()
            : this.listStatusLogs(caseDO.getId());
        return CaseViewConverter.toDetail(caseDO, this.listMedia(caseDO.getId()), this.listAliases(caseDO
            .getId()), statusLogs, this.resolveOperatorNames(statusLogs), regionRepository::nameOf);
    }

    private List<CaseStatusLogDO> listStatusLogs(Long caseId) {
        return caseStatusLogMapper.lambdaQuery()
            .eq(CaseStatusLogDO::getCaseId, caseId)
            .orderByAsc(CaseStatusLogDO::getCreateTime)
            .orderByAsc(CaseStatusLogDO::getId)
            .last("LIMIT " + STATUS_HISTORY_LIMIT)
            .list();
    }

    private List<FindCaseMediaDO> listMedia(Long caseId) {
        return findCaseMediaMapper.lambdaQuery()
            .eq(FindCaseMediaDO::getCaseId, caseId)
            .orderByAsc(FindCaseMediaDO::getSort)
            .orderByAsc(FindCaseMediaDO::getId)
            .list();
    }

    private List<FindCaseAliasDO> listAliases(Long caseId) {
        return findCaseAliasMapper.lambdaQuery().eq(FindCaseAliasDO::getCaseId, caseId).list();
    }

    private Map<Long, List<FindCaseMediaDO>> groupMedia(List<Long> caseIds) {
        return findCaseMediaMapper.lambdaQuery()
            .in(FindCaseMediaDO::getCaseId, caseIds)
            .orderByAsc(FindCaseMediaDO::getSort)
            .orderByAsc(FindCaseMediaDO::getId)
            .list()
            .stream()
            .collect(Collectors.groupingBy(FindCaseMediaDO::getCaseId));
    }

    private Map<Long, List<FindCaseAliasDO>> groupAliases(List<Long> caseIds) {
        return this.listAliasesByCaseIds(caseIds).stream().collect(Collectors.groupingBy(FindCaseAliasDO::getCaseId));
    }

    private List<FindCaseAliasDO> listAliasesByCaseIds(List<Long> caseIds) {
        if (CollUtil.isEmpty(caseIds)) {
            return new ArrayList<>();
        }
        return findCaseAliasMapper.lambdaQuery().in(FindCaseAliasDO::getCaseId, caseIds).list();
    }

    /**
     * 操作人昵称：C 端用户 ID 与后台用户 ID 不同源，只能按 App 用户查，查不到时退化为操作人类型描述
     */
    private Map<Long, String> resolveOperatorNames(List<CaseStatusLogDO> statusLogs) {
        if (CollUtil.isEmpty(statusLogs)) {
            return Collections.emptyMap();
        }
        List<Long> operatorIds = statusLogs.stream()
            .filter(log -> !CaseOperatorTypeEnum.SYSTEM.name().equals(log.getOperatorType()))
            .map(CaseStatusLogDO::getOperatorId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        if (CollUtil.isEmpty(operatorIds)) {
            return Collections.emptyMap();
        }
        return appUserMapper.selectByIds(operatorIds)
            .stream()
            .collect(Collectors.toMap(AppUserDO::getId, user -> StrUtil.blankToDefault(user.getNickname(), "匿名用户"), (
                                                                                                                     existing,
                                                                                                                     replacement) -> existing));
    }
}
