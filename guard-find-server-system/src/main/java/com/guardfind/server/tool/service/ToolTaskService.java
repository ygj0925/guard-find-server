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

import com.guardfind.server.tool.model.req.ToolTaskReq;
import com.guardfind.server.tool.model.resp.ToolTaskResp;

import java.util.List;

/**
 * 家庭组任务分派业务接口(C 端)
 *
 * @author guardfind
 * @since 2026/10/29
 */
public interface ToolTaskService {

    /**
     * 创建任务(创建人须为组成员;负责人须为组成员)
     */
    Long create(ToolTaskReq req);

    /**
     * 任务列表
     *
     * @param scope   mine=指派给我的(跨我的全部组);all=组内全部(须传 groupId)
     * @param groupId scope=all 时必填
     */
    List<ToolTaskResp> list(String scope, Long groupId);

    /**
     * 状态流转(创建者或负责人可推动)
     */
    void updateStatus(Long id, Integer status);

    /**
     * 编辑任务(仅创建者)
     */
    void update(Long id, ToolTaskReq req);

    /**
     * 删除任务(仅创建者)
     */
    void delete(Long id);
}
