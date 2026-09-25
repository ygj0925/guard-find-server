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

package com.guardfind.server.moderation.support;

import cn.hutool.core.util.StrUtil;
import com.guardfind.server.moderation.enums.SensitiveCategoryEnum;
import com.guardfind.server.moderation.model.entity.SensitiveWordDO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 敏感词 DFA Trie（确定有限自动机）
 *
 * <p>
 * 构建后完全不可变，因此可安全地被多线程共享（匹配器以 {@code volatile} 引用整体替换快照即可）。
 * 匹配为"最长优先 + 命中后跳过"，命中词按原文形态返回；比对前统一做小写化并剔除空白与标点，
 * 使"加 微 信 付 款""先-转-钱"之类的拆字规避同样命中。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public final class SensitiveWordTrie {

    /**
     * 空树（词库未就绪或全部停用时复用，避免重复分配）
     */
    private static final SensitiveWordTrie EMPTY = new SensitiveWordTrie(Collections.emptyMap(), new Node());

    private final Map<String, SensitiveCategoryEnum> categoryByWord;
    private final Node root;

    private SensitiveWordTrie(Map<String, SensitiveCategoryEnum> categoryByWord, Node root) {
        this.categoryByWord = Collections.unmodifiableMap(categoryByWord);
        this.root = root;
    }

    /**
     * 空树
     *
     * @return 不含任何词的 Trie
     */
    public static SensitiveWordTrie empty() {
        return EMPTY;
    }

    /**
     * 由启用词表构建 Trie
     *
     * @param words 敏感词实体集合（可为空）
     * @return Trie
     */
    public static SensitiveWordTrie build(Iterable<SensitiveWordDO> words) {
        Map<String, SensitiveCategoryEnum> categoryByWord = new HashMap<>(32);
        Node root = new Node();
        if (words != null) {
            for (SensitiveWordDO word : words) {
                if (word == null || StrUtil.isBlank(word.getWord()) || word.getCategory() == null) {
                    continue;
                }
                String original = StrUtil.trim(word.getWord());
                String normalized = normalize(original);
                if (normalized.isEmpty()) {
                    continue;
                }
                categoryByWord.put(original, word.getCategory());
                Node node = root;
                for (int i = 0; i < normalized.length(); i++) {
                    node = node.children.computeIfAbsent(normalized.charAt(i), key -> new Node());
                }
                node.word = original;
            }
        }
        return new SensitiveWordTrie(categoryByWord, root);
    }

    /**
     * 匹配文本中命中的敏感词（按出现顺序去重）
     *
     * @param text 待匹配文本
     * @return 命中词（原文形态），未命中时为空列表
     */
    public List<String> matchAll(String text) {
        if (StrUtil.isBlank(text) || this.categoryByWord.isEmpty()) {
            return new ArrayList<>();
        }
        String normalized = normalize(text);
        Set<String> hits = new LinkedHashSet<>();
        int index = 0;
        while (index < normalized.length()) {
            MatchResult match = this.matchLongest(index, normalized);
            if (match == null) {
                index++;
                continue;
            }
            hits.add(match.word);
            index = match.end;
        }
        return new ArrayList<>(hits);
    }

    /**
     * 取词所属类别
     *
     * @param word 命中词（原文形态）
     * @return 类别，未知时为 null
     */
    public SensitiveCategoryEnum categoryOf(String word) {
        return this.categoryByWord.get(word);
    }

    /**
     * 词数
     *
     * @return 当前 Trie 的有效词数
     */
    public int wordCount() {
        return this.categoryByWord.size();
    }

    /**
     * 从 {@code start} 起做最长匹配
     *
     * @param start      起始下标
     * @param normalized 归一化后的文本
     * @return 命中的词与其结束下标（不含），未命中时为 null
     */
    private MatchResult matchLongest(int start, String normalized) {
        Node node = this.root;
        MatchResult last = null;
        for (int i = start; i < normalized.length(); i++) {
            Node next = node.children.get(normalized.charAt(i));
            if (next == null) {
                break;
            }
            node = next;
            if (node.word != null) {
                last = new MatchResult(node.word, i + 1);
            }
        }
        return last;
    }

    /**
     * 归一化：小写化 + 仅保留字母与数字（中文按 Unicode 归类为字母，故一并保留）
     *
     * @param text 原始文本
     * @return 归一化文本
     */
    private static String normalize(String text) {
        if (StrUtil.isBlank(text)) {
            return StrUtil.EMPTY;
        }
        String lowered = text.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(lowered.length());
        for (int i = 0; i < lowered.length(); i++) {
            char ch = lowered.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    /**
     * Trie 节点（构建后只读）
     */
    private static final class Node {

        private final Map<Character, Node> children = new HashMap<>(2);

        private String word;
    }

    /**
     * 单次匹配结果
     */
    private static final class MatchResult {

        private final String word;
        private final int end;

        private MatchResult(String word, int end) {
            this.word = word;
            this.end = end;
        }
    }
}
