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

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.guardfind.server.common.base.model.entity.BaseDO;
import lombok.Data;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采集源配置实体
 *
 * <p>
 * 一条配置代表一个可插拔采集源（{@code code} 与 {@code CollectSource#code()} 一一对应）。
 * {@code enabled} 默认关闭，须取得站方书面授权后由运营显式开启；{@code crawlDelayMs} 与站点 robots 的
 * Crawl-delay 取较大值实施同域限速。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@TableName(value = "gf_collect_source", autoResultMap = true)
public class CollectSourceDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 源标识（如 rangaihuijia、local-fixture）
     */
    private String code;

    /**
     * 源名称
     */
    private String name;

    /**
     * 站点根地址
     */
    private String baseUrl;

    /**
     * 列表路径模板（占位符 {categoryId}、{page}）
     */
    private String listPathTpl;

    /**
     * 详情路径模板（占位符 {sourceId}）
     */
    private String detailPathTpl;

    /**
     * 栏目/分类标识（JSON 数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> categoryIds;

    /**
     * 同域抓取间隔（毫秒），不得低于站点声明值
     */
    private Integer crawlDelayMs;

    /**
     * 单轮最大列表页数
     */
    private Integer maxPages;

    /**
     * 请求超时（毫秒）
     */
    private Integer requestTimeoutMs;

    /**
     * 调度表达式
     */
    private String cronExpression;

    /**
     * 是否启用（默认关闭，须书面授权后开启）
     */
    private Boolean enabled;

    /**
     * 授权依据说明
     */
    private String authorizationNote;

    /**
     * 最近一次 robots 判定结果
     */
    private Boolean robotsAllowed;

    /**
     * 上次采集游标（页码等）
     */
    private String lastCursor;

    /**
     * 上次执行时间
     */
    private LocalDateTime lastRunTime;

    /**
     * 备注
     */
    private String remark;
}
