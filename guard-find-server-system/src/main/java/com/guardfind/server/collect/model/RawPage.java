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
import com.guardfind.server.collect.constant.CollectConstants;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 原文页面
 *
 * <p>
 * 一次抓取的最小留痕单位：地址、正文与摘要。摘要在入库前就地计算，避免"存了正文却不知道它有没有被改过"。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
public class RawPage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 采集源标识
     */
    private String sourceCode;

    /**
     * 源侧编号（列表页以 {@code 栏目:页码} 表达）
     */
    private String sourceId;

    /**
     * 来源页面链接
     */
    private String url;

    /**
     * 页面类型（LIST/DETAIL）
     */
    private String pageType = CollectConstants.PAGE_TYPE_DETAIL;

    /**
     * 原文正文
     */
    private String content;

    /**
     * 原文摘要
     */
    private String sha256;

    /**
     * 抓取时间
     */
    private LocalDateTime fetchTime;

    /**
     * 计算并填充正文摘要（幂等，重复调用按当前正文重算）
     *
     * @return 当前对象
     */
    public RawPage fillSha256() {
        this.sha256 = cn.hutool.crypto.digest.DigestUtil.sha256Hex(StrUtil.emptyIfNull(content));
        return this;
    }
}
