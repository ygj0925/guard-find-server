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
import com.guardfind.server.common.enums.DisEnableStatusEnum;
import com.guardfind.server.moderation.constant.ModerationConstants;
import com.guardfind.server.moderation.enums.SensitiveCategoryEnum;
import com.guardfind.server.moderation.mapper.SensitiveWordMapper;
import com.guardfind.server.moderation.model.ContentCheckResult;
import com.guardfind.server.moderation.model.entity.SensitiveWordDO;
import com.guardfind.server.moderation.service.SensitiveWordMatcher;
import com.guardfind.server.moderation.support.SensitiveWordTrie;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.continew.starter.cache.redisson.util.RedisUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 敏感词匹配器实现（内存 DFA Trie + Redis 缓存代号即时失效）
 *
 * <p>
 * 快照整体不可变，仅通过 {@code volatile} 引用替换，因此读路径无锁；重建在 {@code synchronized} 内完成并做双重
 * 检查，缓存抖动时也不会出现同版本重复回源。Redis 不可用不影响匹配可用性，退化为按 {@code SNAPSHOT_MAX_AGE_MILLIS}
 * 周期回源。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveWordMatcherImpl implements SensitiveWordMatcher {

    /**
     * 远端代号不可读（键不存在或 Redis 异常）时的占位版本
     */
    private static final long NO_VERSION = -1L;

    private final SensitiveWordMapper sensitiveWordMapper;

    /**
     * 当前内存快照（不可变，整体替换）
     */
    private volatile Snapshot snapshot;

    @Override
    public ContentCheckResult match(String text) {
        if (StrUtil.isBlank(text)) {
            return ContentCheckResult.pass();
        }
        Snapshot current = this.currentSnapshot();
        List<String> matchedWords = current.trie.matchAll(text);
        if (CollUtil.isEmpty(matchedWords)) {
            return ContentCheckResult.pass();
        }
        List<SensitiveCategoryEnum> categories = new ArrayList<>(matchedWords.size());
        for (String word : matchedWords) {
            categories.add(current.trie.categoryOf(word));
        }
        return ContentCheckResult.of(matchedWords, categories);
    }

    @Override
    public void invalidate() {
        long version = this.bumpRemoteVersion();
        // 本节点立即失效；其余实例由版本号差异在下一次匹配时感知
        this.snapshot = null;
        log.info("敏感词库缓存已失效，远端缓存代号：[{}]，词库将在下一次匹配时重建", version);
    }

    @Override
    public int getWordCount() {
        return this.currentSnapshot().trie.wordCount();
    }

    /**
     * 取得可用快照：版本一致且未过期时直接复用，否则重建
     *
     * @return 快照
     */
    private Snapshot currentSnapshot() {
        Snapshot local = this.snapshot;
        long remote = this.readRemoteVersion();
        if (local != null && this.isUsable(local, remote)) {
            return local;
        }
        return this.rebuild(remote);
    }

    /**
     * 快照是否仍可用（远端不可读时仅受最长有效期约束）
     *
     * @param local  本地快照
     * @param remote 远端缓存代号
     * @return true：可直接使用
     */
    private boolean isUsable(Snapshot local, long remote) {
        boolean versionMatched = remote == NO_VERSION || remote == local.version;
        return versionMatched && !local.isStale();
    }

    /**
     * 重建快照（并发下同一版本只重建一次）
     *
     * @param remote 远端缓存代号
     * @return 新快照
     */
    private Snapshot rebuild(long remote) {
        synchronized (this) {
            Snapshot local = this.snapshot;
            if (local != null && this.isUsable(local, remote)) {
                return local;
            }
            long version = remote == NO_VERSION ? (local == null ? 0L : local.version) : remote;
            SensitiveWordTrie trie;
            try {
                trie = SensitiveWordTrie.build(this.loadEnabledWords());
            } catch (Exception e) {
                // 词库回源失败：沿用旧快照（若有），否则以空树放行，不能让风控故障拖垮提交链路
                log.error("敏感词库回源失败，本次沿用%s快照".formatted(local == null ? "空" : "旧"), e);
                trie = local == null ? SensitiveWordTrie.empty() : local.trie;
            }
            Snapshot rebuilt = new Snapshot(trie, version, System.currentTimeMillis());
            this.snapshot = rebuilt;
            log.info("敏感词库快照已重建：词数 {}，缓存代号 {}", trie.wordCount(), version);
            return rebuilt;
        }
    }

    /**
     * 读取远端缓存代号
     *
     * @return 代号，不可读时为 {@link #NO_VERSION}
     */
    long readRemoteVersion() {
        try {
            Object cached = RedisUtils.get(ModerationConstants.SENSITIVE_WORD_VERSION_KEY);
            if (cached == null) {
                return NO_VERSION;
            }
            return Long.parseLong(StrUtil.trim(cached.toString()));
        } catch (Exception e) {
            log.debug("读取敏感词库缓存代号失败，按远端未知处理：{}", e.getMessage());
            return NO_VERSION;
        }
    }

    /**
     * 递增远端缓存代号
     *
     * @return 新代号，失败时为 {@link #NO_VERSION}
     */
    long bumpRemoteVersion() {
        try {
            return RedisUtils.incr(ModerationConstants.SENSITIVE_WORD_VERSION_KEY);
        } catch (Exception e) {
            log.warn("递增敏感词库缓存代号失败（本节点已立即失效，其余实例将按有效期回源）：{}", e.getMessage());
            return NO_VERSION;
        }
    }

    /**
     * 加载启用词（抽取为独立方法，便于单测隔离数据源）
     *
     * @return 启用词列表
     */
    List<SensitiveWordDO> loadEnabledWords() {
        return sensitiveWordMapper.lambdaQuery().eq(SensitiveWordDO::getStatus, DisEnableStatusEnum.ENABLE).list();
    }

    /**
     * 内存快照（不可变）
     */
    private static final class Snapshot {

        private final SensitiveWordTrie trie;
        private final long version;
        private final long builtAt;

        private Snapshot(SensitiveWordTrie trie, long version, long builtAt) {
            this.trie = trie;
            this.version = version;
            this.builtAt = builtAt;
        }

        /**
         * 是否超过最长有效期（绕过应用直接改库时的兜底）
         *
         * @return true：已过期
         */
        private boolean isStale() {
            return System.currentTimeMillis() - this.builtAt > ModerationConstants.SNAPSHOT_MAX_AGE_MILLIS;
        }
    }
}
