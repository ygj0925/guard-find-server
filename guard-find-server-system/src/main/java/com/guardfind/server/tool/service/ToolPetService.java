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

import com.guardfind.server.tool.model.req.ToolPetLogReq;
import com.guardfind.server.tool.model.req.ToolPetReq;
import com.guardfind.server.tool.model.resp.ToolPetLogResp;
import com.guardfind.server.tool.model.resp.ToolPetResp;

import java.util.List;

/**
 * 宠物成长记录业务接口(C 端)。
 * 权限:仅主人可操作自己的宠物;数据全部个人,不涉及组共享。
 */
public interface ToolPetService {

    Long create(ToolPetReq req);

    void update(Long id, ToolPetReq req);

    void delete(Long id);

    List<ToolPetResp> list();

    Long addLog(Long petId, ToolPetLogReq req);

    void deleteLog(Long petId, Long logId);

    List<ToolPetLogResp> logs(Long petId, String logType);
}
