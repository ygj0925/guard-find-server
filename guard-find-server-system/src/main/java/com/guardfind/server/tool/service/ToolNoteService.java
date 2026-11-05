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

import com.guardfind.server.tool.model.req.ToolNoteReq;
import com.guardfind.server.tool.model.resp.ToolNoteResp;
import top.continew.starter.extension.crud.model.resp.PageResp;

/**
 * 灵感便笺业务接口(C 端)
 *
 * @author guardfind
 * @since 2026/11/06
 */
public interface ToolNoteService {

    /**
     * 新建便笺
     */
    Long create(ToolNoteReq req);

    /**
     * 修改便笺；version 不匹配时返回冲突，避免覆盖他人/其他设备的修改
     */
    void update(Long id, ToolNoteReq req);

    /**
     * 切换置顶状态(校验归属)
     */
    void togglePin(Long id);

    /**
     * 切换归档状态(校验归属)
     */
    void toggleArchive(Long id);

    /**
     * 删除便笺(校验归属)
     */
    void delete(Long id);

    /**
     * 当前用户便笺分页：置顶优先，其余按更新时间倒序；keyword 同时匹配标题与正文
     */
    PageResp<ToolNoteResp> page(int page, int size, String keyword, boolean archived);
}
