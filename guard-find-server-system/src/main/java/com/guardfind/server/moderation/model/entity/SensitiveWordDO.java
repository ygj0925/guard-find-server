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

package com.guardfind.server.moderation.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.guardfind.server.common.base.model.entity.BaseDO;
import com.guardfind.server.common.enums.DisEnableStatusEnum;
import com.guardfind.server.moderation.enums.SensitiveCategoryEnum;
import lombok.Data;

import java.io.Serial;

/**
 * 敏感词实体
 *
 * <p>
 * 词库是匹配器的唯一数据源：仅 {@code status = ENABLE} 的词进入内存 Trie，停用词在下次重建时自然失效。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@TableName("gf_sensitive_word")
public class SensitiveWordDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 敏感词
     */
    private String word;

    /**
     * 类别（ILLEGAL/ABUSE/FRAUD）
     */
    private SensitiveCategoryEnum category;

    /**
     * 状态（1：启用；2：停用）
     */
    private DisEnableStatusEnum status;

    /**
     * 备注
     */
    private String remark;
}
