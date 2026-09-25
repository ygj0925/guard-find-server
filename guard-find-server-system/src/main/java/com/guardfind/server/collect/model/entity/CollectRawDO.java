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

package com.guardfind.server.collect.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableName;
import com.guardfind.server.common.base.model.entity.BaseDO;
import lombok.Data;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 采集原文快照实体
 *
 * <p>
 * {@code (sourceCode, sourceId)} 唯一，重跑同一页面时按摘要比对而非新增行；{@code sha256} 与
 * {@code sourceUrl}/{@code fetchedAt} 在正文按留存期清理后仍需保留，以便对外答复"采过什么、何时采的"。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@TableName("gf_collect_raw")
public class CollectRawDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 采集源标识
     */
    private String sourceCode;

    /**
     * 源侧编号
     */
    private String sourceId;

    /**
     * 来源页面链接
     */
    private String sourceUrl;

    /**
     * 页面类型（LIST/DETAIL）
     */
    private String pageType;

    /**
     * 原文正文（留存期后清理）
     */
    private String content;

    /**
     * 原文摘要
     */
    private String sha256;

    /**
     * 抓取时间
     */
    private LocalDateTime fetchedAt;

    /**
     * 正文是否已按留存策略清理
     */
    private Boolean erased;
}
