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

package com.guardfind.server.findcase.service;

/**
 * 内容安全检查网关
 *
 * <p>
 * 档案域只依赖这一个方法，具体的敏感词/欺诈词匹配器由治理域（{@code com.guardfind.server.moderation}）实现：
 * 命中欺诈类（如"先转钱""交核实费"）应返回拒绝原因，命中违法类应直接拒绝。治理域接入后本接口的实现替换为真实匹配器即可，
 * 档案域无需改动。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public interface ContentCheckGateway {

    /**
     * 检查文本是否可对外发布
     *
     * @param text 待检查文本（可为空，空文本视为通过）
     * @return 未命中返回 {@code null}；命中返回拒绝原因（用于直接展示给用户）
     */
    String check(String text);
}
