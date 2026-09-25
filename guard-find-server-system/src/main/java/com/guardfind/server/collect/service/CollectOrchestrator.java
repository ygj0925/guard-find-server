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

package com.guardfind.server.collect.service;

import com.guardfind.server.collect.model.resp.CollectRunResp;

/**
 * 采集编排业务接口
 *
 * <p>
 * 一轮采集的完整流程：读源配置 → robots 判定 → 翻页取条目地址 → 逐条抓详情 → 原文快照 upsert → 归一化入库 →
 * 去重收敛 → 批次统计落库。主流程只依赖 {@code CollectSource} SPI，不认任何具体站点。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public interface CollectOrchestrator {

    /**
     * 执行一轮采集
     *
     * @param sourceCode  源标识
     * @param maxPages    本轮最大列表页数（为空则取源配置，且永不超过配置值）
     * @param triggerType 触发方式（JOB/MANUAL）
     * @return 批次执行结果
     */
    CollectRunResp run(String sourceCode, Integer maxPages, String triggerType);
}
