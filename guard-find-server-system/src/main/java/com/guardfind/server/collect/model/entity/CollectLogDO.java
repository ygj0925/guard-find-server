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

/**
 * 采集批次日志实体
 *
 * <p>
 * 一轮采集的可观测出口：页数、抓取数、新增、重复、失败、耗时、实测同域间隔与错误摘要。
 * 因 robots 拒绝而中止的轮次也会落一行（{@code success=false} + {@code skippedReason}），
 * 保证"没采"与"采了什么"同样可查。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@TableName("gf_collect_log")
public class CollectLogDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 批次号
     */
    private String batchNo;

    /**
     * 采集源标识
     */
    private String sourceCode;

    /**
     * 触发方式（JOB/MANUAL）
     */
    private String triggerType;

    /**
     * 抓取列表页数
     */
    private Integer pages;

    /**
     * 抓取详情数
     */
    private Integer fetched;

    /**
     * 新增候选数
     */
    private Integer added;

    /**
     * 重复数
     */
    private Integer duplicated;

    /**
     * 失败数
     */
    private Integer failed;

    /**
     * 跳过/中止原因（如 robots 拒绝）
     */
    private String skippedReason;

    /**
     * 耗时（毫秒）
     */
    private Long costMs;

    /**
     * 实测最小同域间隔（毫秒）
     */
    private Integer minIntervalMs;

    /**
     * 错误摘要
     */
    private String errorSummary;

    /**
     * 是否成功
     */
    private Boolean success;
}
