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

package com.guardfind.server.lead.enums;

/**
 * 线索回复发起方（对应 {@code gf_lead_reply.from_side}）
 *
 * @author guardfind
 * @since 2026/9/24
 */
public enum LeadReplyFromEnum {

    /**
     * 档案所有人（家属）
     */
    OWNER("档案所有人"),

    /**
     * 线索提交人（好心人）
     */
    SUBMITTER("提交人");

    private final String description;

    LeadReplyFromEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
