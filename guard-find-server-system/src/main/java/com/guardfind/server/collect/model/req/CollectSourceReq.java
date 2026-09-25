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

package com.guardfind.server.collect.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 采集源创建或修改请求参数
 *
 * <p>
 * {@code enabled} 缺省为 {@code false}：新增源不会因"忘了关"而被调度任务偷偷采起来，
 * 且 {@code authorizationNote} 用于记录书面授权依据，作为开启采集的前置说明。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "采集源创建或修改请求参数")
public class CollectSourceReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 源标识
     */
    @Schema(description = "源标识", example = "rangaihuijia")
    @NotBlank(message = "源标识不能为空")
    @Length(max = 32, message = "源标识长度不能超过 {max} 个字符")
    private String code;

    /**
     * 源名称
     */
    @Schema(description = "源名称", example = "公开寻亲库（关爱回家）")
    @NotBlank(message = "源名称不能为空")
    @Length(max = 64, message = "源名称长度不能超过 {max} 个字符")
    private String name;

    /**
     * 站点根地址
     */
    @Schema(description = "站点根地址", example = "https://www.example.com")
    @NotBlank(message = "站点根地址不能为空")
    @Length(max = 255, message = "站点根地址长度不能超过 {max} 个字符")
    private String baseUrl;

    /**
     * 列表路径模板
     */
    @Schema(description = "列表路径模板", example = "/xunren/category/{categoryId}?p-page={page}")
    @Length(max = 255, message = "列表路径模板长度不能超过 {max} 个字符")
    private String listPathTpl;

    /**
     * 详情路径模板
     */
    @Schema(description = "详情路径模板", example = "/xunren/view/{sourceId}")
    @Length(max = 255, message = "详情路径模板长度不能超过 {max} 个字符")
    private String detailPathTpl;

    /**
     * 栏目/分类标识
     */
    @Schema(description = "栏目/分类标识")
    private List<String> categoryIds;

    /**
     * 同域抓取间隔（毫秒）
     */
    @Schema(description = "同域抓取间隔（毫秒），不得低于站点声明值", example = "2000")
    @Min(value = 0, message = "同域抓取间隔不能小于 {value}")
    @Max(value = 600000, message = "同域抓取间隔不能大于 {value}")
    private Integer crawlDelayMs;

    /**
     * 单轮最大列表页数
     */
    @Schema(description = "单轮最大列表页数", example = "5")
    @Min(value = 1, message = "单轮最大列表页数不能小于 {value}")
    @Max(value = 100, message = "单轮最大列表页数不能大于 {value}")
    private Integer maxPages;

    /**
     * 请求超时（毫秒）
     */
    @Schema(description = "请求超时（毫秒）", example = "10000")
    @Min(value = 1000, message = "请求超时不能小于 {value} 毫秒")
    @Max(value = 120000, message = "请求超时不能大于 {value} 毫秒")
    private Integer requestTimeoutMs;

    /**
     * 调度表达式
     */
    @Schema(description = "调度表达式", example = "0 30 3 * * ?")
    @Length(max = 64, message = "调度表达式长度不能超过 {max} 个字符")
    private String cronExpression;

    /**
     * 是否启用
     */
    @Schema(description = "是否启用（默认关闭，须书面授权后开启）", example = "false")
    private Boolean enabled;

    /**
     * 授权依据说明
     */
    @Schema(description = "授权依据说明")
    @Length(max = 500, message = "授权依据说明长度不能超过 {max} 个字符")
    private String authorizationNote;

    /**
     * 备注
     */
    @Schema(description = "备注")
    @Length(max = 500, message = "备注长度不能超过 {max} 个字符")
    private String remark;
}
