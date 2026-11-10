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

package com.guardfind.server.app.controller;

import com.guardfind.server.app.model.req.AppClientLogReq;
import com.guardfind.server.app.util.ClientLogMasker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppClientLogControllerTest {

    @Test
    @DisplayName("手机号(含空格/+86)、身份证、token 在服务端被强制掩码")
    void masksSensitiveValues() {
        assertEquals("call 138****5678", ClientLogMasker.mask("call 138 1234 5678"));
        assertEquals("+86 138****5678", ClientLogMasker.mask("+86 13812345678"));
        assertEquals("id 110105********002X", ClientLogMasker.mask("id 11010519491231002X"));
        assertEquals("Authorization: abcdef****", ClientLogMasker.mask("Authorization: abcdef123456789"));
        assertEquals("{\"token\":\"****\"}", ClientLogMasker.mask("{\"token\":\"secret-value\"}"));
    }

    @Test
    @DisplayName("日志行包含用户与路由,extra 也被掩码,换行被转义防伪造")
    void formatsSingleMaskedLine() {
        AppClientLogReq req = new AppClientLogReq();
        req.setLevel("ERROR");
        req.setMessage("boom\nfake line 13812345678");
        req.setPlatform("android");
        req.setRoute("/publish/case");
        req.setExtra(Map.of("phone", "13900001111"));
        req.setStack("at a\nat b");

        String line = AppClientLogController.format(req, 42L);

        assertTrue(line.startsWith("user=42 platform=android version=- route=/publish/case"));
        assertTrue(line.contains("139****1111"));
        assertTrue(line.contains("boom\\nfake line 138****5678"));
        assertFalse(line.contains("\n"));
        assertFalse(line.contains("13812345678"));
    }

    @Test
    @DisplayName("匿名上报记为 user=-")
    void anonymousUser() {
        AppClientLogReq req = new AppClientLogReq();
        req.setLevel("WARN");
        req.setMessage("offline");
        assertTrue(AppClientLogController.format(req, null).startsWith("user=- "));
    }
}
