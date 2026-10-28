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

import com.guardfind.server.tool.model.req.ToolCountdownReq;
import com.guardfind.server.tool.model.resp.ToolCountdownResp;

import java.util.List;

/**
 * 倒数日业务接口(C 端)
 *
 * @author guardfind
 * @since 2026/10/28
 */
public interface ToolCountdownService {

    /**
     * 新建倒数日
     */
    Long create(ToolCountdownReq req);

    /**
     * 修改倒数日(校验归属)
     */
    void update(Long id, ToolCountdownReq req);

    /**
     * 删除倒数日(校验归属)
     */
    void delete(Long id);

    /**
     * 当前用户倒数日列表(按目标日期升序,已过的排最后)
     */
    List<ToolCountdownResp> list();
}
