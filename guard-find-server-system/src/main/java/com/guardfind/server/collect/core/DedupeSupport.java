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

import com.guardfind.server.collect.model.entity.CollectCandidateDO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 去重收敛规则
 *
 * <p>
 * 同 {@code dedupeKey} 的候选保留"信息最完整"的一条作为主候选（{@code PENDING}），其余转 {@code DUPLICATED}
 * 并把 {@code primaryId} 指向主候选；完整度相同时先入库者为主，保证重跑与并发下的结果稳定可复现。
 * 已清除（{@code ERASED}）的候选不参与收敛，避免把新采到的条目挂到一条已撤回的历史记录上。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public final class DedupeSupport {

    /**
     * 主候选比较器：完整度降序 → 创建时间升序 → ID 升序
     */
    private static final Comparator<CollectCandidateDO> PRIMARY_COMPARATOR = Comparator.comparing((
                                                                                                   CollectCandidateDO candidate) -> candidate
                                                                                                       .getCompleteness() == null
                                                                                                           ? BigDecimal.ZERO
                                                                                                           : candidate
                                                                                                               .getCompleteness(), Comparator
                                                                                                                   .reverseOrder())
        .thenComparing(candidate -> candidate.getCreateTime() == null ? LocalDateTime.MIN : candidate.getCreateTime())
        .thenComparing(candidate -> candidate.getId() == null ? Long.MAX_VALUE : candidate.getId());

    private DedupeSupport() {
    }

    /**
     * 选出主候选
     *
     * @param candidates 同指纹候选集合（非空）
     * @return 主候选，集合为空时返回 null
     */
    public static CollectCandidateDO pickPrimary(List<CollectCandidateDO> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream().min(PRIMARY_COMPARATOR).orElse(null);
    }

    /**
     * 完整度比较（null 安全）
     *
     * @param left  左侧
     * @param right 右侧
     * @return 正数表示左侧更完整
     */
    public static int compareCompleteness(BigDecimal left, BigDecimal right) {
        return (left == null ? BigDecimal.ZERO : left).compareTo(right == null ? BigDecimal.ZERO : right);
    }

    /**
     * 新候选是否比既有候选集合更完整（相等时不改主，避免重跑抖动）
     *
     * @param incoming 新候选
     * @param existing 既有候选
     * @return true：新候选应成为主候选
     */
    public static boolean isNewPrimary(CollectCandidateDO incoming, CollectCandidateDO existing) {
        return compareCompleteness(incoming.getCompleteness(), existing.getCompleteness()) > 0;
    }
}
