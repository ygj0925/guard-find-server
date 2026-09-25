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

package com.guardfind.server.findcase.service;

import com.guardfind.server.findcase.model.query.CaseMineQuery;
import com.guardfind.server.findcase.model.req.CaseActionReq;
import com.guardfind.server.findcase.model.req.CaseFoundReq;
import com.guardfind.server.findcase.model.req.CaseReq;
import com.guardfind.server.findcase.model.req.CaseSubmitReq;
import com.guardfind.server.findcase.model.resp.CaseDetailResp;
import com.guardfind.server.findcase.model.resp.CaseMinePageResp;
import com.guardfind.server.findcase.model.resp.CasePublicResp;
import jakarta.validation.Valid;
import top.continew.starter.extension.crud.model.query.PageQuery;

/**
 * 寻人档案业务接口
 *
 * @author guardfind
 * @since 2026/9/24
 */
public interface CaseService {

    /**
     * 创建档案
     *
     * @param req 请求参数（{@code submit=true} 时直接进入待审核）
     * @return 完整视图（仅创建者可见自身完整信息）
     */
    CaseDetailResp create(@Valid CaseReq req);

    /**
     * 修改档案（草稿/驳回可全量修改；已发布仅可补充信息）
     *
     * @param req 请求参数
     * @param id  档案 ID
     * @return 完整视图
     */
    CaseDetailResp update(@Valid CaseReq req, Long id);

    /**
     * 提交审核（完整性校验、疑似重复检测、版本快照、紧急程度重算）
     *
     * @param id  档案 ID
     * @param req 请求参数
     * @return 完整视图
     */
    CaseDetailResp submit(Long id, CaseSubmitReq req);

    /**
     * 确认找回
     *
     * @param id  档案 ID
     * @param req 找回确认参数
     */
    void found(Long id, @Valid CaseFoundReq req);

    /**
     * 续期（重置 90 天时效；已归档档案续期后重新发布）
     *
     * @param id  档案 ID
     * @param req 动作参数
     */
    void renew(Long id, CaseActionReq req);

    /**
     * 撤回发布
     *
     * @param id  档案 ID
     * @param req 动作参数
     */
    void withdraw(Long id, CaseActionReq req);

    /**
     * 删除档案（响应删除权：清除识别字段并回收媒体文件）
     *
     * @param id  档案 ID
     * @param req 动作参数
     */
    void erase(Long id, CaseActionReq req);

    /**
     * 公开详情（脱敏；不可对外可见的状态返回"不存在"）
     *
     * @param id 档案 ID
     * @return 公开视图
     */
    CasePublicResp getPublic(Long id);

    /**
     * 完整详情（本人/志愿者/审核可见，写入访问留痕）
     *
     * @param id 档案 ID
     * @return 完整视图
     */
    CaseDetailResp getFull(Long id);

    /**
     * 我的发布（分页 + 各状态计数）
     *
     * @param query     查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    CaseMinePageResp mine(CaseMineQuery query, PageQuery pageQuery);

    /**
     * 时效任务：超期档案归档 + 归档前提醒（由 {@code CaseExpiryJob} 调度）
     *
     * @return 本次归档的档案数
     */
    int runExpiryTask();
}
