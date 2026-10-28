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

package com.guardfind.server.ai.support;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 拆解输出的 JSON 解析(纯函数)
 *
 * <p>
 * 模型偶尔会包一层 ```json 围栏或前后废话,这里做宽容提取:
 * 找到第一个 '[' 到最后一个 ']' 之间解析;失败返回 null,由调用方降级。
 * </p>
 */
public final class DecomposeParser {

    private DecomposeParser() {
    }

    /**
     * @return 拆解出的子步骤;无法解析返回 null(2-8 条,超界截断/拒绝)
     */
    public static List<String> parse(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) {
            return null;
        }
        int start = llmOutput.indexOf('[');
        int end = llmOutput.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JSONArray array = JSONUtil.parseArray(llmOutput.substring(start, end + 1));
            List<String> items = new ArrayList<>();
            for (Object item : array) {
                String text = String.valueOf(item).trim();
                if (!text.isEmpty()) {
                    items.add(text);
                }
            }
            if (items.size() < 2 || items.size() > 8) {
                return null;
            }
            return items;
        } catch (Exception e) {
            return null;
        }
    }
}
