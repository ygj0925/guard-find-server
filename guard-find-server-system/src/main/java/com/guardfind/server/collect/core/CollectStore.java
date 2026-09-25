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

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guardfind.server.collect.constant.CollectConstants;
import com.guardfind.server.collect.enums.CandidateStatusEnum;
import com.guardfind.server.collect.mapper.CollectCandidateMapper;
import com.guardfind.server.collect.mapper.CollectLogMapper;
import com.guardfind.server.collect.mapper.CollectRawMapper;
import com.guardfind.server.collect.mapper.CollectSourceMapper;
import com.guardfind.server.collect.model.RawPage;
import com.guardfind.server.collect.model.entity.CollectCandidateDO;
import com.guardfind.server.collect.model.entity.CollectLogDO;
import com.guardfind.server.collect.model.entity.CollectRawDO;
import com.guardfind.server.collect.model.entity.CollectSourceDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 采集落库出口
 *
 * <p>
 * 把"原文快照 upsert、候选读写、批次日志"这组与采集流程强绑定的存储动作收在一处：编排器只表达流程，
 * 不掺 Wrapper 细节，也便于单测以内存替身验证重跑幂等与去重收敛。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectStore {

    private final CollectSourceMapper sourceMapper;
    private final CollectRawMapper rawMapper;
    private final CollectCandidateMapper candidateMapper;
    private final CollectLogMapper logMapper;

    /**
     * 按标识查询源配置
     *
     * @param code 源标识
     * @return 源配置
     */
    public Optional<CollectSourceDO> findSource(String code) {
        return Optional.ofNullable(sourceMapper.selectOne(Wrappers.<CollectSourceDO>lambdaQuery()
            .eq(CollectSourceDO::getCode, code)
            .last("LIMIT 1")));
    }

    /**
     * 已启用的源（调度只遍历这些；{@code enabled} 默认关闭）
     *
     * @return 源配置列表
     */
    public List<CollectSourceDO> listEnabledSources() {
        return sourceMapper.selectList(Wrappers.<CollectSourceDO>lambdaQuery()
            .eq(CollectSourceDO::getEnabled, Boolean.TRUE)
            .orderByAsc(CollectSourceDO::getId));
    }

    /**
     * 回写本轮 robots 判定与游标
     *
     * @param sourceId      源配置 ID
     * @param robotsAllowed 最近一次 robots 判定结果
     * @param cursor        本轮游标（页码）
     */
    public void markSourceRun(Long sourceId, boolean robotsAllowed, String cursor) {
        if (sourceId == null) {
            return;
        }
        CollectSourceDO update = new CollectSourceDO();
        update.setId(sourceId);
        update.setRobotsAllowed(robotsAllowed);
        update.setLastCursor(cursor);
        update.setLastRunTime(LocalDateTime.now());
        sourceMapper.updateById(update);
    }

    /**
     * 原文快照 upsert（{@code source_code + source_id} 唯一）
     *
     * <p>
     * 摘要一致时不重写正文，只刷新批次可见的抓取时间以外字段保持不变；摘要变化才更新正文与新摘要，
     * 使"重跑同一页集合"既不产生重复行也不无谓改写。
     * </p>
     *
     * @param page 原文页面
     * @return 快照 ID
     */
    public Long upsertRaw(RawPage page) {
        String sha256 = StrUtil.blankToDefault(page.getSha256(), this.sha256Of(page.getContent()));
        CollectRawDO existing = rawMapper.selectOne(Wrappers.<CollectRawDO>lambdaQuery()
            .eq(CollectRawDO::getSourceCode, page.getSourceCode())
            .eq(CollectRawDO::getSourceId, page.getSourceId())
            .last("LIMIT 1"));
        LocalDateTime now = page.getFetchTime() == null ? LocalDateTime.now() : page.getFetchTime();
        if (existing == null) {
            CollectRawDO raw = new CollectRawDO();
            raw.setSourceCode(page.getSourceCode());
            raw.setSourceId(page.getSourceId());
            raw.setSourceUrl(page.getUrl());
            raw.setPageType(StrUtil.blankToDefault(page.getPageType(), CollectConstants.PAGE_TYPE_DETAIL));
            raw.setContent(page.getContent());
            raw.setSha256(sha256);
            raw.setFetchedAt(now);
            raw.setErased(Boolean.FALSE);
            rawMapper.insert(raw);
            return raw.getId();
        }
        if (!StrUtil.equals(existing.getSha256(), sha256)) {
            // 内容变化：更新快照与摘要，同时撤销已清理标记（重新进入留存期）
            rawMapper.lambdaUpdate()
                .set(CollectRawDO::getContent, page.getContent())
                .set(CollectRawDO::getSha256, sha256)
                .set(CollectRawDO::getSourceUrl, page.getUrl())
                .set(CollectRawDO::getFetchedAt, now)
                .set(CollectRawDO::getErased, Boolean.FALSE)
                .eq(CollectRawDO::getId, existing.getId())
                .update();
        }
        return existing.getId();
    }

    /**
     * 按来源定位候选（重跑幂等判据）
     *
     * @param sourceCode 源标识
     * @param sourceId   源侧编号
     * @return 候选
     */
    public Optional<CollectCandidateDO> findCandidate(String sourceCode, String sourceId) {
        return Optional.ofNullable(candidateMapper.selectOne(Wrappers.<CollectCandidateDO>lambdaQuery()
            .eq(CollectCandidateDO::getSourceCode, sourceCode)
            .eq(CollectCandidateDO::getSourceId, sourceId)
            .last("LIMIT 1")));
    }

    /**
     * 同指纹候选集合（排除自身与已清除项）
     *
     * @param dedupeKey 去重指纹
     * @param excludeId 排除的候选 ID
     * @return 同指纹候选
     */
    public List<CollectCandidateDO> listByDedupeKey(String dedupeKey, Long excludeId) {
        if (StrUtil.isBlank(dedupeKey)) {
            return List.of();
        }
        return candidateMapper.selectList(Wrappers.<CollectCandidateDO>lambdaQuery()
            .eq(CollectCandidateDO::getDedupeKey, dedupeKey)
            .ne(CollectCandidateDO::getStatus, CandidateStatusEnum.ERASED)
            .ne(excludeId != null, CollectCandidateDO::getId, excludeId));
    }

    /**
     * 新增候选
     *
     * @param candidate 候选
     * @return 候选 ID
     */
    public Long insertCandidate(CollectCandidateDO candidate) {
        candidateMapper.insert(candidate);
        return candidate.getId();
    }

    /**
     * 更新候选
     *
     * @param candidate 候选（需含 ID）
     */
    public void updateCandidate(CollectCandidateDO candidate) {
        candidateMapper.updateById(candidate);
    }

    /**
     * 更新候选的收敛状态
     *
     * @param candidateId 候选 ID
     * @param status      状态
     * @param primaryId   主候选 ID
     */
    public void updateCandidateStatus(Long candidateId, CandidateStatusEnum status, Long primaryId) {
        candidateMapper.lambdaUpdate()
            .set(CollectCandidateDO::getStatus, status)
            .set(CollectCandidateDO::getPrimaryId, primaryId)
            .eq(CollectCandidateDO::getId, candidateId)
            .update();
    }

    /**
     * 落批次日志
     *
     * @param logDO 日志
     */
    public void insertLog(CollectLogDO logDO) {
        logMapper.insert(logDO);
    }

    /**
     * 计算正文摘要
     *
     * @param content 正文
     * @return SHA-256
     */
    public String sha256Of(String content) {
        return cn.hutool.crypto.digest.DigestUtil.sha256Hex(StrUtil.emptyIfNull(content));
    }
}
