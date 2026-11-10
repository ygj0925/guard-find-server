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

package com.guardfind.server.moderation;

import com.guardfind.server.moderation.enums.SensitiveCategoryEnum;
import com.guardfind.server.moderation.model.ContentCheckResult;
import com.guardfind.server.moderation.model.entity.SensitiveWordDO;
import com.guardfind.server.moderation.support.SensitiveWordTrie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 敏感词 DFA 与分级判定单元测试
 */
class SensitiveWordTrieTest {

    private static SensitiveWordDO word(String text, SensitiveCategoryEnum category) {
        SensitiveWordDO word = new SensitiveWordDO();
        word.setWord(text);
        word.setCategory(category);
        return word;
    }

    private final SensitiveWordTrie trie = SensitiveWordTrie.build(List
        .of(word("先转钱", SensitiveCategoryEnum.FRAUD), word("加微信付款", SensitiveCategoryEnum.FRAUD), word("贩卖儿童", SensitiveCategoryEnum.ILLEGAL), word("傻瓜", SensitiveCategoryEnum.ABUSE)));

    private ContentCheckResult check(String text) {
        List<String> hits = trie.matchAll(text);
        return ContentCheckResult.of(hits, hits.stream().map(trie::categoryOf).toList());
    }

    @Test
    @DisplayName("拆字与标点规避同样命中")
    void splitCharactersStillMatch() {
        assertThat(trie.matchAll("请 先-转-钱 再说")).containsExactly("先转钱");
        assertThat(trie.matchAll("加 微 信 付 款")).containsExactly("加微信付款");
        assertThat(trie.matchAll("正常的寻人描述")).isEmpty();
    }

    @Test
    @DisplayName("ILLEGAL/ABUSE 命中拒绝提交")
    void illegalAndAbuseRejected() {
        assertThat(check("有人贩卖儿童").isRejected()).isTrue();
        assertThat(check("你个傻瓜").isRejected()).isTrue();
    }

    @Test
    @DisplayName("FRAUD 命中不拒绝但标高风险入审核队列")
    void fraudFlaggedHighRisk() {
        ContentCheckResult result = check("找到孩子请先转钱");
        assertThat(result.isRejected()).isFalse();
        assertThat(result.isHighRisk()).isTrue();
        assertThat(result.getReason()).contains("先转钱");
    }

    @Test
    @DisplayName("FRAUD 与 ILLEGAL 同时命中按最坏类别拒绝，不再标高风险")
    void worstCategoryWins() {
        ContentCheckResult result = check("先转钱，贩卖儿童");
        assertThat(result.isRejected()).isTrue();
        assertThat(result.isHighRisk()).isFalse();
        assertThat(result.getMatchedWords()).containsExactly("先转钱", "贩卖儿童");
    }

    @Test
    @DisplayName("未命中即通过；空词库不误伤")
    void passWhenNoHit() {
        assertThat(check("正常描述").isHit()).isFalse();
        assertThat(SensitiveWordTrie.empty().matchAll("先转钱")).isEmpty();
    }
}
