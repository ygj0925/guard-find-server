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

package com.guardfind.server.collect.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import com.guardfind.server.common.base.model.resp.BaseDetailResp;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采集源响应参数
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "采集源响应参数")
public class CollectSourceResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 源标识
     */
    @Schema(description = "源标识", example = "rangaihuijia")
    private String code;

    /**
     * 源名称
     */
    @Schema(description = "源名称", example = "公开寻亲库（关爱回家）")
    private String name;

    /**
     * 站点根地址
     */
    @Schema(description = "站点根地址", example = "https://www.example.com")
    private String baseUrl;

    /**
     * 列表路径模板
     */
    @Schema(description = "列表路径模板", example = "/xunren/category/{categoryId}?p-page={page}")
    private String listPathTpl;

    /**
     * 详情路径模板
     */
    @Schema(description = "详情路径模板", example = "/xunren/view/{sourceId}")
    private String detailPathTpl;

    /**
     * 栏目/分类标识
     */
    @Schema(description = "栏目/分类标识")
    private List<String> categoryIds;

    /**
     * 同域抓取间隔（毫秒）
     */
    @Schema(description = "同域抓取间隔（毫秒）", example = "2000")
    private Integer crawlDelayMs;

    /**
     * 单轮最大列表页数
     */
    @Schema(description = "单轮最大列表页数", example = "5")
    private Integer maxPages;

    /**
     * 请求超时（毫秒）
     */
    @Schema(description = "请求超时（毫秒）", example = "10000")
    private Integer requestTimeoutMs;

    /**
     * 调度表达式
     */
    @Schema(description = "调度表达式", example = "0 30 3 * * ?")
    private String cronExpression;

    /**
     * 是否启用
     */
    @Schema(description = "是否启用", example = "false")
    private Boolean enabled;

    /**
     * 授权依据说明
     */
    @Schema(description = "授权依据说明")
    private String authorizationNote;

    /**
     * 最近一次 robots 判定结果
     */
    @Schema(description = "最近一次 robots 判定结果", example = "true")
    private Boolean robotsAllowed;

    /**
     * 上次采集游标
     */
    @Schema(description = "上次采集游标", example = "page=5")
    private String lastCursor;

    /**
     * 上次执行时间
     */
    @Schema(description = "上次执行时间", example = "2026-09-24 03:30:00", type = "string")
    private LocalDateTime lastRunTime;

    /**
     * 备注
     */
    @Schema(description = "备注")
    private String remark;
}
