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

package com.guardfind.server.moderation.service;

import com.guardfind.server.moderation.enums.ModerationBizTypeEnum;
import com.guardfind.server.moderation.model.query.ModerationQuery;
import com.guardfind.server.moderation.model.query.ReportQueueQuery;
import com.guardfind.server.moderation.model.req.ModerationApproveReq;
import com.guardfind.server.moderation.model.req.ModerationRejectReq;
import com.guardfind.server.moderation.model.req.ReportHandleReq;
import com.guardfind.server.moderation.model.resp.ModerationItemResp;
import com.guardfind.server.moderation.model.resp.ReportQueueItemResp;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;

/**
 * 内容治理业务接口（后台）
 *
 * <p>
 * 审核动作必须同时留下"谁审的、为什么、什么时候"三要素并触发状态变更与通知，因此档案类结论一律经
 * {@code CaseStatusService} 状态漏斗落库，本服务不直接改档案状态。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public interface ModerationService {

    /**
     * 待审核队列（按提交时间升序，先进先出）
     *
     * @param query     查询条件
     * @param pageQuery 分页参数
     * @return 分页响应
     */
    PageResp<ModerationItemResp> pending(ModerationQuery query, PageQuery pageQuery);

    /**
     * 举报队列（按举报时间升序）
     *
     * @param query     查询条件
     * @param pageQuery 分页参数
     * @return 分页响应
     */
    PageResp<ReportQueueItemResp> reportPage(ReportQueueQuery query, PageQuery pageQuery);

    /**
     * 审核通过
     *
     * @param bizType 业务类型
     * @param id      业务 ID
     * @param req     请求参数
     */
    void approve(ModerationBizTypeEnum bizType, Long id, ModerationApproveReq req);

    /**
     * 审核驳回（理由码必填且取自字典 {@code gf_reject_reason}）
     *
     * @param bizType 业务类型
     * @param id      业务 ID
     * @param req     请求参数
     */
    void reject(ModerationBizTypeEnum bizType, Long id, ModerationRejectReq req);

    /**
     * 处置举报（结果通知双方，可选登记用户处置）
     *
     * @param id  举报 ID
     * @param req 请求参数
     */
    void handleReport(Long id, ReportHandleReq req);
}
