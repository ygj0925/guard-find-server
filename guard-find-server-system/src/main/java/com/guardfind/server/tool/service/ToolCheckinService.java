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

import com.guardfind.server.tool.model.req.ToolCheckinCreateReq;
import com.guardfind.server.tool.model.req.ToolCheckinReq;
import com.guardfind.server.tool.model.resp.ToolCheckinResp;

import java.util.List;

/**
 * 签到业务接口(C 端)
 *
 * @author guardfind
 * @since 2026/10/29
 */
public interface ToolCheckinService {

    /**
     * 发起签到(自动生成动态码种子;默认时间窗=现在起 2 小时)
     */
    Long create(ToolCheckinCreateReq req);

    /**
     * 我发起的签到列表
     */
    List<ToolCheckinResp> myActivities();

    /**
     * 活动详情:发起人=带动态码种子与签到名单;参与者=带是否已签
     */
    ToolCheckinResp detail(Long id);

    /**
     * 执行签到:校验时间窗/动态码/地点名/重复
     */
    Long checkin(Long id, ToolCheckinReq req);

    /**
     * 我签到的活动列表
     */
    List<ToolCheckinResp> signedByMe();

    /**
     * 删除签到活动(仅发起人,级联删记录)
     */
    void delete(Long id);
}
