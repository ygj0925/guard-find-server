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

package com.guardfind.server.ai.service;

import com.guardfind.server.ai.model.resp.AiTextResp;

/**
 * AI 洞察接口:把工具箱已有数据(记账/待办/习惯)喂给 LLM 生成文本。
 * 全部复用现有 AI 代理与数据服务,不引入外部依赖。
 *
 * @author guardfind
 * @since 2026/10/28
 */
public interface AiInsightService {

    /**
     * 个人周报:本周记账汇总 + 待办完成 + 习惯打卡 → 一段周报
     *
     * @param date 周内锚点日(yyyy-MM-dd,可空=今天)
     */
    AiTextResp weeklyReport(String date);

    /**
     * 把一个目标拆成 3-5 个可执行小步骤(待办页"AI 拆解"用)
     */
    AiTextResp decompose(String content);

    /**
     * 记账周期点评(周/月)
     */
    AiTextResp ledgerInsight(String period, String date);
}
