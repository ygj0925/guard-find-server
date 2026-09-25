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

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.guardfind.server.collect.constant.CollectConstants;
import com.guardfind.server.collect.core.CandidateMapper;
import com.guardfind.server.collect.core.CollectStore;
import com.guardfind.server.collect.core.DedupeSupport;
import com.guardfind.server.collect.core.DomainRateLimiter;
import com.guardfind.server.collect.core.RobotsPolicy;
import com.guardfind.server.collect.enums.CandidateStatusEnum;
import com.guardfind.server.collect.model.CandidateDraft;
import com.guardfind.server.collect.model.RobotsDecision;
import com.guardfind.server.collect.model.SourceConfig;
import com.guardfind.server.collect.model.entity.CollectCandidateDO;
import com.guardfind.server.collect.model.entity.CollectLogDO;
import com.guardfind.server.collect.model.entity.CollectSourceDO;
import com.guardfind.server.collect.model.resp.CollectRunResp;
import com.guardfind.server.collect.service.CollectOrchestrator;
import com.guardfind.server.collect.source.CollectSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.continew.starter.core.exception.BusinessException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 采集编排业务实现
 *
 * <p>
 * 流程与容错口径：①robots 判定不通过（禁止 / 拿不到 / 解析失败）立即结束本轮，且不发起任何详情抓取，中止原因落
 * {@code gf_collect_log.skipped_reason}；②列表页与详情页单条失败只记失败明细，<b>整批不失败</b>；
 * ③原文按 {@code source_code + source_id} upsert，摘要未变不重写；④同 {@code source_code + source_id} 已有候选时
 * 直接跳过，故重跑同一页集合新增为 0；⑤同指纹按"完整度优先、既有优先"收敛，落选者转 {@code DUPLICATED} 并指向主候选。
 * </p>
 *
 * <p>
 * 全流程不在方法级开启事务：一轮采集跨越几十次外部请求，长事务会把连接占满，且单条失败也不该回滚已完成的入库。
 * 每条候选的快照 + 入库 + 收敛是各自独立的短写操作，重跑由来源唯一键与幂等判据兜底。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectOrchestratorImpl implements CollectOrchestrator {

    /**
     * 可作为主候选的状态（已驳回与已清除不参与收敛，避免把新条目挂到无效历史条目上）
     */
    private static final Set<CandidateStatusEnum> PRIMARY_ELIGIBLE = EnumSet
        .of(CandidateStatusEnum.PENDING, CandidateStatusEnum.APPROVED, CandidateStatusEnum.MERGED);

    private final CollectStore store;
    private final RobotsPolicy robotsPolicy;
    private final DomainRateLimiter rateLimiter;
    private final CandidateMapper candidateMapper;
    private final List<CollectSource> collectSources;

    @Override
    public CollectRunResp run(String sourceCode, Integer maxPages, String triggerType) {
        CollectSourceDO sourceDO = store.findSource(StrUtil.trim(sourceCode))
            .orElseThrow(() -> new BusinessException("采集源 [%s] 不存在".formatted(sourceCode)));
        CollectSource source = this.resolveSource(sourceDO.getCode());
        SourceConfig config = SourceConfig.of(sourceDO);
        if (maxPages != null && maxPages > 0) {
            config.setMaxPages(Math.min(maxPages, config.getMaxPages()));
        }
        String batchNo = DateUtil.format(LocalDateTime.now(), CollectConstants.BATCH_NO_PATTERN);
        CollectRunResp result = new CollectRunResp();
        result.setBatchNo(batchNo);
        result.setSourceCode(config.getCode());
        List<String> errors = new ArrayList<>();
        long start = System.currentTimeMillis();
        try {
            this.doRun(source, config, sourceDO, batchNo, result, errors);
        } catch (Exception e) {
            // 未预期异常同样要留下批次日志，运维查日志时不应出现"跑了但没记录"
            result.setSuccess(false);
            result.setSkippedReason(StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName()));
            errors.add("本轮异常终止：" + e.getMessage());
            log.error("采集源 [{}] 本轮执行异常", config.getCode(), e);
        } finally {
            result.setCostMs(System.currentTimeMillis() - start);
            result.setErrors(errors);
            Long gap = this.observedGap(config);
            result.setMinIntervalMs(gap == null ? null : gap.intValue());
            this.saveLog(config, batchNo, triggerType, result);
        }
        return result;
    }

    /**
     * 主流程（robots 门禁 → 翻页 → 逐条采集 → 收敛）
     */
    private void doRun(CollectSource source,
                       SourceConfig config,
                       CollectSourceDO sourceDO,
                       String batchNo,
                       CollectRunResp result,
                       List<String> errors) {
        RobotsDecision decision = null;
        if (source.robotsRequired()) {
            decision = robotsPolicy.check(config);
            store.markSourceRun(sourceDO.getId(), decision.isAllowed(), "batch=" + batchNo);
            if (!decision.isAllowed()) {
                result.setSuccess(false);
                result.setSkippedReason(decision.getReason());
                log.warn("采集源 [{}] 本轮中止：{}", config.getCode(), decision.getReason());
                return;
            }
            // 站点声明的 Crawl-delay 与配置值取较大者，限速只许更保守
            config.setCrawlDelayMs(rateLimiter.requiredIntervalMs((int)config.getCrawlDelayMs(), decision
                .getCrawlDelayMillis()));
        }
        List<String> entryUrls = this.collectEntryUrls(source, config, decision, result, errors);
        if (entryUrls.isEmpty()) {
            return;
        }
        int duplicated = 0;
        for (String url : entryUrls) {
            if (decision != null && !decision.allows(url)) {
                result.setFailed(result.getFailed() + 1);
                errors.add("robots 禁止抓取，跳过：" + url);
                continue;
            }
            try {
                duplicated += this.collectDetail(source, config, url, batchNo, result, errors);
            } catch (Exception e) {
                // 单条详情页失败不影响同批其余条目
                result.setFailed(result.getFailed() + 1);
                errors.add("详情页处理失败：" + url + "（" + StrUtil.blankToDefault(e.getMessage(), e.getClass()
                    .getSimpleName()) + "）");
                log.warn("采集源 [{}] 详情页处理失败：{}，原因：{}", config.getCode(), url, e.getMessage());
            }
        }
        result.setDuplicated(duplicated);
    }

    /**
     * 翻页收集详情条目地址（受 maxPages 保护；空页提前结束，跳过原因写进日志）
     */
    private List<String> collectEntryUrls(CollectSource source,
                                          SourceConfig config,
                                          RobotsDecision decision,
                                          CollectRunResp result,
                                          List<String> errors) {
        List<String> entryUrls = new ArrayList<>();
        for (int page = 1; page <= config.getMaxPages(); page++) {
            List<String> urls;
            try {
                urls = source.fetchListPageUrls(config, page);
            } catch (Exception e) {
                result.setFailed(result.getFailed() + 1);
                errors.add("列表页第 " + page + " 页失败：" + StrUtil.blankToDefault(e.getMessage(), e.getClass()
                    .getSimpleName()));
                log.warn("采集源 [{}] 列表页第 {} 页失败：{}", config.getCode(), page, e.getMessage());
                continue;
            }
            result.setPages(page);
            if (CollUtil.isEmpty(urls)) {
                String reason = "第 " + page + " 页无条目，已提前结束翻页（配置上限 " + config.getMaxPages() + " 页）";
                result.setSkippedReason(StrUtil.blankToDefault(result.getSkippedReason(), reason));
                break;
            }
            for (String url : urls) {
                if (StrUtil.isNotBlank(url) && !entryUrls.contains(url)) {
                    entryUrls.add(url);
                }
            }
        }
        if (result.getPages() >= config.getMaxPages() && result.getSkippedReason() == null) {
            result.setSkippedReason("已达单轮页数上限 " + config.getMaxPages() + " 页，剩余页次留待下轮");
        }
        return entryUrls;
    }

    /**
     * 采集一条详情页
     *
     * @return 本次新增的重复（收敛）条目数
     */
    private int collectDetail(CollectSource source,
                              SourceConfig config,
                              String url,
                              String batchNo,
                              CollectRunResp result,
                              List<String> errors) {
        Optional<CandidateDraft> draft = source.fetchDetail(config, url);
        if (draft.isEmpty()) {
            result.setFailed(result.getFailed() + 1);
            errors.add("页面不可用或未解析出有效字段：" + url);
            return 0;
        }
        CandidateDraft item = draft.get();
        if (StrUtil.isBlank(item.getSourceId()) || item.getRaw() == null) {
            result.setFailed(result.getFailed() + 1);
            errors.add("缺少源侧编号或原文，无法留痕：" + url);
            return 0;
        }
        result.setFetched(result.getFetched() + 1);
        // 重跑幂等：同来源已入库过就直接跳过，既不新增候选也不重复写快照
        if (store.findCandidate(config.getCode(), item.getSourceId()).isPresent()) {
            store.upsertRaw(item.getRaw());
            return 1;
        }
        Long rawId = store.upsertRaw(item.getRaw());
        CollectCandidateDO candidate = candidateMapper.map(item, config, batchNo);
        candidate.setRawId(rawId);
        store.insertCandidate(candidate);
        result.setAdded(result.getAdded() + 1);
        return this.converge(candidate);
    }

    /**
     * 同指纹收敛：主候选保留 {@code PENDING}（已审批/已合并者不动），落选者转 {@code DUPLICATED} 并指向主候选
     *
     * @return 本条引发的重复条目数
     */
    private int converge(CollectCandidateDO incoming) {
        List<CollectCandidateDO> sameKey = store.listByDedupeKey(incoming.getDedupeKey(), incoming.getId())
            .stream()
            .filter(item -> PRIMARY_ELIGIBLE.contains(item.getStatus()))
            .toList();
        if (sameKey.isEmpty()) {
            return 0;
        }
        List<CollectCandidateDO> all = new ArrayList<>(sameKey);
        all.add(incoming);
        CollectCandidateDO primary = DedupeSupport.pickPrimary(all);
        if (primary == null) {
            return 0;
        }
        int duplicated = 0;
        if (Objects.equals(primary.getId(), incoming.getId())) {
            for (CollectCandidateDO item : sameKey) {
                if (item.getStatus() == CandidateStatusEnum.PENDING) {
                    store.updateCandidateStatus(item.getId(), CandidateStatusEnum.DUPLICATED, primary.getId());
                    duplicated++;
                }
            }
        } else {
            CollectCandidateDO update = new CollectCandidateDO();
            update.setId(incoming.getId());
            update.setStatus(CandidateStatusEnum.DUPLICATED);
            update.setPrimaryId(primary.getId());
            store.updateCandidate(update);
            duplicated++;
        }
        return duplicated;
    }

    /**
     * 按源标识定位采集实现（新增站点只需再注册一个 {@link CollectSource} Bean）
     */
    private CollectSource resolveSource(String code) {
        return collectSources.stream()
            .filter(item -> StrUtil.equalsIgnoreCase(item.code(), code))
            .findFirst()
            .orElseThrow(() -> new BusinessException("未找到采集源实现 [%s]，请确认代码已发布".formatted(code)));
    }

    /**
     * 取实测最小同域间隔（以站点根地址为归组键，与抓取时一致）
     */
    private Long observedGap(SourceConfig config) {
        if (StrUtil.isBlank(config.getBaseUrl())) {
            return null;
        }
        return rateLimiter.minObservedGap(RobotsPolicy.robotsUrlOf(config.getBaseUrl()));
    }

    /**
     * 批次统计落库（含中止轮次）
     */
    private void saveLog(SourceConfig config, String batchNo, String triggerType, CollectRunResp result) {
        CollectLogDO logDO = new CollectLogDO();
        logDO.setBatchNo(batchNo);
        logDO.setSourceCode(config.getCode());
        logDO.setTriggerType(StrUtil.blankToDefault(triggerType, CollectConstants.TRIGGER_MANUAL));
        logDO.setPages(result.getPages());
        logDO.setFetched(result.getFetched());
        logDO.setAdded(result.getAdded());
        logDO.setDuplicated(result.getDuplicated());
        logDO.setFailed(result.getFailed());
        logDO.setSkippedReason(StrUtil.subPre(result.getSkippedReason(), 255));
        logDO.setCostMs(result.getCostMs());
        logDO.setMinIntervalMs(result.getMinIntervalMs());
        logDO.setErrorSummary(this.summarize(result.getErrors()));
        logDO.setSuccess(result.isSuccess());
        try {
            store.insertLog(logDO);
        } catch (Exception e) {
            log.error("采集批次日志落库失败：批次 {}，源 {}", batchNo, config.getCode(), e);
        }
    }

    /**
     * 错误摘要（截断到列宽内，保留条数便于判断影响面）
     */
    private String summarize(List<String> errors) {
        if (CollUtil.isEmpty(errors)) {
            return null;
        }
        String summary = "失败 %d 条：%s".formatted(errors.size(), String.join(" | ", errors));
        return StrUtil.subPre(summary, CollectConstants.ERROR_SUMMARY_MAX_LENGTH);
    }
}
