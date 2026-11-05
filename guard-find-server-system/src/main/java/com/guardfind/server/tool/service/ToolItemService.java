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

import com.guardfind.server.tool.model.req.ToolItemReq;
import com.guardfind.server.tool.model.resp.ToolItemResp;
import top.continew.starter.extension.crud.model.resp.PageResp;

/**
 * 物品收纳业务接口(C 端)
 *
 * @author guardfind
 * @since 2026/11/06
 */
public interface ToolItemService {

    /**
     * 新建物品
     */
    Long create(ToolItemReq req);

    /**
     * 修改物品；version 不匹配时返回冲突
     */
    void update(Long id, ToolItemReq req);

    /**
     * 增减数量；结果不能为负，写回时校验当前值避免并发覆盖
     */
    void changeQuantity(Long id, int delta);

    /**
     * 切换归档状态(校验归属)
     */
    void toggleArchive(Long id);

    /**
     * 删除物品(校验归属)
     */
    void delete(Long id);

    /**
     * 当前用户物品分页：keyword 匹配名称/位置/备注，location 单独按位置过滤
     */
    PageResp<ToolItemResp> page(int page, int size, String keyword, String location, boolean archived);
}
