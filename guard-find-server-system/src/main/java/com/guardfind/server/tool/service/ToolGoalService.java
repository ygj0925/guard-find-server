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

import com.guardfind.server.tool.model.req.ToolGoalReq;
import com.guardfind.server.tool.model.req.ToolKeyResultReq;
import com.guardfind.server.tool.model.req.ToolKrProgressReq;
import com.guardfind.server.tool.model.resp.ToolGoalResp;

import java.util.List;

/**
 * 目标 OKR 业务接口(C 端,个人维度)
 *
 * @author guardfind
 * @since 2026/10/29
 */
public interface ToolGoalService {

    /**
     * 创建目标
     */
    Long create(ToolGoalReq req);

    /**
     * 目标列表(带 KR 与进度)
     *
     * @param period yyyy 或 yyyyQn,可空=全部周期
     */
    List<ToolGoalResp> list(String period);

    /**
     * 修改目标描述(仅本人)
     */
    void update(Long id, ToolGoalReq req);

    /**
     * 删除目标(级联删 KR)
     */
    void delete(Long id);

    /**
     * 新增 KR
     */
    Long createKr(Long goalId, ToolKeyResultReq req);

    /**
     * 更新 KR 进度;当前值 >= 目标值时自动置为已完成
     */
    void updateKrProgress(Long krId, ToolKrProgressReq req);

    /**
     * 手动标记 KR 完成/恢复
     */
    void toggleKr(Long krId);

    /**
     * 删除 KR
     */
    void deleteKr(Long krId);
}
