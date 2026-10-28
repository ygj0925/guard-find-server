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

package com.guardfind.server.ai;

import com.guardfind.server.ai.support.DecomposeParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link DecomposeParser} 单测:宽容提取 JSON 数组,过滤围栏/废话,条数边界。
 */
class DecomposeParserTest {

    @Test
    @DisplayName("裸 JSON 数组直接解析")
    void plainArray() {
        List<String> items = DecomposeParser.parse("[\"第一步\",\"第二步\",\"第三步\"]");
        assertEquals(3, items.size());
        assertEquals("第一步", items.get(0));
    }

    @Test
    @DisplayName("带 ```json 围栏和前后废话也能提取")
    void fencedArray() {
        String output = "好的,拆解如下:\n```json\n[\"a\",\"b\",\"c\",\"d\"]\n```\n加油!";
        List<String> items = DecomposeParser.parse(output);
        assertEquals(4, items.size());
    }

    @Test
    @DisplayName("少于 2 条或超过 8 条拒绝")
    void sizeBounds() {
        assertNull(DecomposeParser.parse("[\"只有一条\"]"));
        StringBuilder tooMany = new StringBuilder("[");
        for (int i = 0; i < 9; i++) {
            tooMany.append("\"").append(i).append("\",");
        }
        tooMany.setLength(tooMany.length() - 1);
        tooMany.append("]");
        assertNull(DecomposeParser.parse(tooMany.toString()));
    }

    @Test
    @DisplayName("空串/无数组/坏 JSON 返回 null")
    void invalidInput() {
        assertNull(DecomposeParser.parse(""));
        assertNull(DecomposeParser.parse(null));
        assertNull(DecomposeParser.parse("没有数组的回答"));
        assertNull(DecomposeParser.parse("[\"坏\","));
    }
}
