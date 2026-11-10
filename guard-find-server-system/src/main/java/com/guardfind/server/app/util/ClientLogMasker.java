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

package com.guardfind.server.app.util;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 客户端日志服务端强制掩码:不信任客户端已掩码,落日志前再过一遍。
 *
 * @author guardfind
 * @since 2026/11/10
 */
public final class ClientLogMasker {

    private record Rule(Pattern pattern, String replacement) {}

    /** 身份证须先于手机号,否则 18 位串的中段会先被当成手机号。 */
    private static final List<Rule> RULES = List.of(new Rule(Pattern
        .compile("(?<!\\d)(\\d{6})\\d{8}(\\d{3}[0-9Xx])(?!\\d)"), "$1********$2"), new Rule(Pattern
            .compile("(\\+?86[-\\s]?)?(1[3-9]\\d)[-\\s]?\\d{4}[-\\s]?(\\d{4})"), "$1$2****$3"), new Rule(Pattern
                .compile("(?i)((?:Bearer|Authorization)\\s*[:=]\\s*)([A-Za-z0-9._-]{6})[A-Za-z0-9._-]*"), "$1$2****"), new Rule(Pattern
                    .compile("(?i)(\"(?:token|password|oldPassword|newPassword|contactValue|idCard)\"\\s*:\\s*\")[^\"]*(\")"), "$1****$2"));

    private ClientLogMasker() {
    }

    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        for (Rule rule : RULES) {
            result = rule.pattern().matcher(result).replaceAll(rule.replacement());
        }
        return result;
    }
}
