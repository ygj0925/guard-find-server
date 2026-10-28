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

package com.guardfind.server.tool.service;

import com.guardfind.server.tool.model.req.ToolSplitReq;
import com.guardfind.server.tool.model.resp.ToolSplitResp;

import java.util.List;

/**
 * AA 分账业务接口(C 端)
 *
 * @author guardfind
 * @since 2026/10/28
 */
public interface ToolSplitService {

    /**
     * 保存分账记录
     */
    Long create(ToolSplitReq req);

    /**
     * 分账历史(按日期倒序)
     */
    List<ToolSplitResp> list();

    /**
     * 删除分账记录(校验归属)
     */
    void delete(Long id);

    /**
     * 标记已写回记账(幂等)
     */
    void markWrittenBack(Long id);
}
