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

package com.guardfind.server.collect.service;

import com.guardfind.server.collect.model.query.CandidateQuery;
import com.guardfind.server.collect.model.query.CollectLogQuery;
import com.guardfind.server.collect.model.req.CandidateRejectReq;
import com.guardfind.server.collect.model.req.CollectEraseReq;
import com.guardfind.server.collect.model.resp.CandidateResp;
import com.guardfind.server.collect.model.resp.CollectEraseResp;
import com.guardfind.server.collect.model.resp.CollectLogResp;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.util.List;

/**
 * 采集候选审核业务接口
 *
 * <p>
 * 采集数据 MUST NOT 直接进入广场：候选只有经审批（或合并）才会落成对外可见的寻人档案，且审批与合并都必须幂等。
 * 站方或权利人提出撤回时，按来源批量转 {@code ERASED} 并留下审计。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public interface CandidateService {

    /**
     * 候选分页
     *
     * @param query     查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResp<CandidateResp> page(CandidateQuery query, PageQuery pageQuery);

    /**
     * 候选详情（含来源链接、抓取时间、原文摘要与站内疑似重复档案）
     *
     * @param id 候选 ID
     * @return 候选
     */
    CandidateResp get(Long id);

    /**
     * 审批入库（幂等：重复审批返回既有档案 ID，不产生第二条档案）
     *
     * @param id         候选 ID
     * @param operatorId 操作人
     * @return 档案 ID
     */
    Long approve(Long id, Long operatorId);

    /**
     * 合并到既有档案（仅追加来源关联，不新建档案、不覆盖原发布信息）
     *
     * @param id         候选 ID
     * @param caseId     目标档案 ID
     * @param operatorId 操作人
     * @return 档案 ID
     */
    Long merge(Long id, Long caseId, Long operatorId);

    /**
     * 驳回
     *
     * @param id         候选 ID
     * @param req        驳回参数
     * @param operatorId 操作人
     */
    void reject(Long id, CandidateRejectReq req, Long operatorId);

    /**
     * 批量删除候选（软删，不动已发布档案）
     *
     * @param ids 候选 ID 列表
     */
    void delete(List<Long> ids);

    /**
     * 按来源批量撤回清除（候选与其生成的档案转 ERASED，并写删除审计）
     *
     * @param req 撤回参数
     * @return 执行结果
     */
    CollectEraseResp erase(CollectEraseReq req);

    /**
     * 批次日志分页
     *
     * @param query     查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResp<CollectLogResp> pageLog(CollectLogQuery query, PageQuery pageQuery);
}
