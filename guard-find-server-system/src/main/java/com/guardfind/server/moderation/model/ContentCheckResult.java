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

package com.guardfind.server.moderation.model;

import com.guardfind.server.moderation.enums.SensitiveCategoryEnum;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 内容安全检查结果
 *
 * <p>
 * 一次匹配同时给出"命中了哪些词"与"最坏类别"，调用方据此分流：
 * {@link #isRejected()} 为真应拒绝提交，{@link #isHighRisk()} 为真应放行但标记高风险并进入人工审核队列。
 * 两者互斥（{@code FRAUD} 不拒绝），因此同一次命中既被拒又被标高的情况不会出现。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Getter
public class ContentCheckResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 通过（未命中任何词）
     */
    private static final ContentCheckResult PASS = new ContentCheckResult(Collections.emptyList(), null);

    /**
     * 命中词（原文形态，按出现顺序去重）
     */
    private final List<String> matchedWords;

    /**
     * 最坏类别（严重度最高者），未命中时为 null
     */
    private final SensitiveCategoryEnum worstCategory;

    private ContentCheckResult(List<String> matchedWords, SensitiveCategoryEnum worstCategory) {
        this.matchedWords = matchedWords;
        this.worstCategory = worstCategory;
    }

    /**
     * 构造通过结果（复用同一实例）
     *
     * @return 通过结果
     */
    public static ContentCheckResult pass() {
        return PASS;
    }

    /**
     * 构造检查结果
     *
     * @param matchedWords 命中词
     * @param categories   与命中词一一对应的类别
     * @return 检查结果
     */
    public static ContentCheckResult of(List<String> matchedWords, List<SensitiveCategoryEnum> categories) {
        if (matchedWords == null || matchedWords.isEmpty()) {
            return PASS;
        }
        SensitiveCategoryEnum worst = null;
        for (SensitiveCategoryEnum category : categories) {
            worst = SensitiveCategoryEnum.worst(worst, category);
        }
        // 拒绝类命中时保留全部命中词便于展示，非拒绝类同样保留
        return new ContentCheckResult(new ArrayList<>(matchedWords), worst);
    }

    /**
     * 是否命中词库
     *
     * @return true：命中
     */
    public boolean isHit() {
        return !this.matchedWords.isEmpty();
    }

    /**
     * 是否应拒绝提交（命中 ILLEGAL/ABUSE）
     *
     * @return true：拒绝
     */
    public boolean isRejected() {
        return this.worstCategory != null && this.worstCategory.isReject();
    }

    /**
     * 是否应标记高风险（命中 FRAUD 且未到拒绝级别）
     *
     * @return true：高风险
     */
    public boolean isHighRisk() {
        return this.worstCategory == SensitiveCategoryEnum.FRAUD;
    }

    /**
     * 面向用户的说明文案（未命中时为 null）
     *
     * @return 说明文案
     */
    public String getReason() {
        if (this.worstCategory == null) {
            return null;
        }
        return "涉嫌「%s」（命中：%s）".formatted(this.worstCategory.getDescription(), String.join("、", this.matchedWords));
    }
}
