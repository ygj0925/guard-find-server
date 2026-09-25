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

package com.guardfind.server.moderation.service.impl;

import cn.hutool.core.util.StrUtil;
import com.guardfind.server.findcase.service.ContentCheckGateway;
import com.guardfind.server.moderation.enums.SensitiveCategoryEnum;
import com.guardfind.server.moderation.model.ContentCheckResult;
import com.guardfind.server.moderation.service.SensitiveWordMatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 内容安全检查网关（治理域真实实现）
 *
 * <p>
 * 档案域只通过 {@link ContentCheckGateway} 这一个方法依赖内容风控，此处委托敏感词匹配器给出结论。
 * 与线索链路的差别在于"欺诈话术"的处置口径：线索命中 {@code FRAUD} 只是可疑（好心人转述诈骗套路），放行并转人工；
 * 而家属自填的档案文本出现 {@code FRAUD} 意味着号商/中介借寻亲名义行骗，无"待核实"可言，直接拒绝并给出可执行提示。
 * {@code ILLEGAL}/{@code ABUSE} 两条链路一律拒绝。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Component
@Primary
@RequiredArgsConstructor
public class SensitiveWordContentCheckGateway implements ContentCheckGateway {

    private final SensitiveWordMatcher sensitiveWordMatcher;

    @Override
    public String check(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        ContentCheckResult result = sensitiveWordMatcher.match(text);
        if (!result.isHit()) {
            return null;
        }
        if (result.isRejected()) {
            return result.getReason();
        }
        return "涉嫌「%s」（命中：%s），寻亲档案不得包含索要钱财、私下交易等内容；若您掌握线索，请在档案详情页点击「提供线索」提交".formatted(SensitiveCategoryEnum.FRAUD
            .getDescription(), String.join("、", result.getMatchedWords()));
    }
}
