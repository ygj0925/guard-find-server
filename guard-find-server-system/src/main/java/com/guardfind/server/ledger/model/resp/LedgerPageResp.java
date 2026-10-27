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

package com.guardfind.server.ledger.model.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.io.Serial;
import java.util.List;

/**
 * 记账分页响应（契约字段名 records）
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "记账分页响应")
public class LedgerPageResp extends PageResp<LedgerRecordResp> {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    @JsonProperty("records")
    public List<LedgerRecordResp> getList() {
        return super.getList();
    }

    /**
     * 构造分页响应
     */
    public static LedgerPageResp of(List<LedgerRecordResp> list, long total) {
        LedgerPageResp resp = new LedgerPageResp();
        resp.setList(list);
        resp.setTotal(total);
        return resp;
    }
}
