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

package com.guardfind.server.lead.service;

import com.guardfind.server.lead.model.query.LeadQuery;
import com.guardfind.server.lead.model.req.LeadReplyReq;
import com.guardfind.server.lead.model.req.LeadReq;
import com.guardfind.server.lead.model.req.LeadStatusReq;
import com.guardfind.server.lead.model.resp.LeadPageResp;
import com.guardfind.server.lead.model.resp.LeadReplyResp;
import com.guardfind.server.lead.model.resp.LeadResp;
import top.continew.starter.extension.crud.model.query.PageQuery;

/**
 * 线索业务接口
 *
 * <p>
 * 线索是"好心人 → 家属"的单向通道，本域负责三件事：入口收紧（绑手机 + 限频 + 内容风控）、
 * 出口脱敏（联系方式仅档案所有人可见明文）、双向留痕（状态处置与回复并同步站内信）。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public interface LeadService {

    /**
     * 提交线索
     *
     * @param req    请求参数
     * @param caseId 档案 ID
     * @return 线索视图（提交人视角，联系方式掩码）
     */
    LeadResp submit(LeadReq req, Long caseId);

    /**
     * 分页查询某档案的线索（仅档案发布人与志愿者）
     *
     * @param caseId    档案 ID
     * @param pageQuery 分页参数
     * @return 分页响应
     */
    LeadPageResp pageByCase(Long caseId, PageQuery pageQuery);

    /**
     * 分页查询我提交的线索
     *
     * @param query     查询条件
     * @param pageQuery 分页参数
     * @return 分页响应
     */
    LeadPageResp mine(LeadQuery query, PageQuery pageQuery);

    /**
     * 处置线索状态（仅档案发布人）
     *
     * @param id  线索 ID
     * @param req 请求参数
     */
    void updateStatus(Long id, LeadStatusReq req);

    /**
     * 回复线索（仅档案发布人）
     *
     * @param id  线索 ID
     * @param req 请求参数
     * @return 回复视图
     */
    LeadReplyResp reply(Long id, LeadReplyReq req);
}
