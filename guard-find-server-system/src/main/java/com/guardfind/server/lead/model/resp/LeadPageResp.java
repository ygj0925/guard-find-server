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

package com.guardfind.server.lead.model.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.io.Serial;
import java.util.List;

/**
 * 线索分页响应
 *
 * <p>
 * 契约要求列表字段名为 {@code records}（{@code PageResp} 默认序列化为 {@code list}），此处仅重写序列化键名，
 * 分页能力仍复用 {@link PageResp}。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "线索分页响应")
public class LeadPageResp extends PageResp<LeadResp> {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 列表数据（契约字段名 records）
     *
     * @return 列表数据
     */
    @Override
    @JsonProperty("records")
    public List<LeadResp> getList() {
        return super.getList();
    }

    /**
     * 构造分页响应
     *
     * @param list  列表数据
     * @param total 总数
     * @return 分页响应
     */
    public static LeadPageResp of(List<LeadResp> list, long total) {
        LeadPageResp resp = new LeadPageResp();
        resp.setList(list);
        resp.setTotal(total);
        return resp;
    }
}
