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

package com.guardfind.server.collect.model;

import cn.hutool.core.util.StrUtil;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 候选草稿（解析后、归一化前的页面字段）
 *
 * <p>
 * 刻意保持"页面怎么写就怎么读"的原文形态：日期、性别、区划等归一化统一由
 * {@code collect.core.CandidateMapper} 完成，保证真实源与本地样例源走同一套归一规则。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
public class CandidateDraft implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 原文快照（含正文、摘要、抓取时间）
     */
    private RawPage raw;

    /**
     * 源侧编号
     */
    private String sourceId;

    /**
     * 来源页面链接
     */
    private String sourceUrl;

    /**
     * 类别原文
     */
    private String categoryText;

    /**
     * 姓名原文
     */
    private String name;

    /**
     * 别名原文
     */
    private List<String> aliases;

    /**
     * 性别原文（男/女/M/F 等）
     */
    private String genderText;

    /**
     * 出生日期原文
     */
    private String birthdayText;

    /**
     * 失踪时年龄原文
     */
    private String ageText;

    /**
     * 失踪日期原文
     */
    private String missingDateText;

    /**
     * 失踪地址原文（含区划）
     */
    private String regionText;

    /**
     * 详细地址原文
     */
    private String addressText;

    /**
     * 失踪时身高原文
     */
    private String heightText;

    /**
     * 特征描述原文
     */
    private String featuresText;

    /**
     * 发生经过原文
     */
    private String narrativeText;

    /**
     * 家庭背景及线索资料原文
     */
    private String familyCluesText;

    /**
     * 其他说明原文
     */
    private String otherNoteText;

    /**
     * 寻亲联系人原文（源页面公开内容，原样留痕不做用途）
     */
    private String contactText;

    /**
     * 照片地址原文
     */
    private String coverUrl;

    /**
     * 是否没有任何可识别字段（脏页面判定，交由编排器记失败明细而非中断整批）
     *
     * @return true：整页未解析出姓名与来源编号
     */
    public boolean isUnresolved() {
        return StrUtil.isBlank(name) && StrUtil.isBlank(sourceId);
    }
}
