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

package com.guardfind.server.moderation.constant;

import com.guardfind.server.common.constant.CacheConstants;

/**
 * 内容治理域常量
 *
 * @author guardfind
 * @since 2026/9/24
 */
public final class ModerationConstants {

    /**
     * 站内消息业务类型：举报
     */
    public static final String MESSAGE_BIZ_TYPE_REPORT = "REPORT";

    /**
     * 举报理由字典编码
     */
    public static final String DICT_REPORT_REASON = "gf_report_reason";

    /**
     * 审核驳回理由字典编码
     */
    public static final String DICT_REJECT_REASON = "gf_reject_reason";

    /**
     * 处置类型字典编码
     */
    public static final String DICT_PUNISHMENT_TYPE = "gf_punishment_type";

    /**
     * 敏感词库缓存代号键（词库变更后自增，匹配器据此即时重建内存 Trie）
     */
    public static final String SENSITIVE_WORD_VERSION_KEY = "MODERATION" + CacheConstants.DELIMITER + "SENSITIVE_WORD" + CacheConstants.DELIMITER + "VERSION";

    /**
     * 内存词库快照的最长有效期（毫秒）：绕过应用直接改库时的兜底刷新周期
     */
    public static final long SNAPSHOT_MAX_AGE_MILLIS = 5 * 60 * 1000L;

    /**
     * 举报达到该有效次数后提升档案审核优先级
     */
    public static final int REPORT_HIT_THRESHOLD = 3;

    /**
     * 举报证据图片上限
     */
    public static final int MAX_REPORT_MEDIA_COUNT = 3;

    /**
     * 单个敏感词最大长度（Trie 构建时截断防御）
     */
    public static final int MAX_WORD_LENGTH = 64;

    /**
     * 待审核列表的最大页长
     */
    public static final int MAX_PAGE_SIZE = 100;

    private ModerationConstants() {
    }
}
