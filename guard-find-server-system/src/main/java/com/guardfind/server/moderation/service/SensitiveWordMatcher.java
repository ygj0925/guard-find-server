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

package com.guardfind.server.moderation.service;

import com.guardfind.server.moderation.model.ContentCheckResult;

/**
 * 敏感词匹配器
 *
 * <p>
 * 词库来自 {@code gf_sensitive_word} 中 {@code status = 启用} 的行，构建为内存 DFA Trie；
 * 匹配为纯内存操作，单次调用代价约等于文本长度，可在提交流程上高频调用。
 * </p>
 *
 * <p>
 * 即时生效：实现类以 Redis 缓存代号（generation）键比对本地快照版本，词库变更后调用 {@link #invalidate()}
 * 即可让所有实例在下一次匹配时重建，无需重启；直接改库的兜底是快照最长有效期。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public interface SensitiveWordMatcher {

    /**
     * 匹配文本
     *
     * @param text 待检查文本（可为空，空文本视为通过）
     * @return 检查结果，恒不为 null
     */
    ContentCheckResult match(String text);

    /**
     * 使当前内存词库失效（词库增删改后调用）
     */
    void invalidate();

    /**
     * 当前内存快照的词数（用于后台自检）
     *
     * @return 词数
     */
    int getWordCount();
}
