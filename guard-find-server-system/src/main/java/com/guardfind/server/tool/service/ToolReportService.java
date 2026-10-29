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

import com.guardfind.server.tool.model.req.ToolReportCommentReq;
import com.guardfind.server.tool.model.req.ToolReportCreateReq;
import com.guardfind.server.tool.model.req.ToolReportEntryReq;
import com.guardfind.server.tool.model.resp.ToolReportCommentResp;
import com.guardfind.server.tool.model.resp.ToolReportEntryResp;
import com.guardfind.server.tool.model.resp.ToolReportResp;

import java.util.List;

/**
 * 月度工作汇报业务接口(C 端)。
 *
 * <p>
 * 权限矩阵(全部服务端强制):
 * 查看活动/成员/已提交月报/评论 = 活动成员(OWNER/REPORTER/VIEWER);
 * 填报 = 仅 REPORTER 写本人 entry;草稿 = 仅本人可读;
 * 评论回复 = 全体成员;管理(删活动/加成员) = 仅 OWNER。
 * 非成员访问一律 NOT_FOUND,不泄露活动存在性。
 * </p>
 *
 * @author guardfind
 * @since 2026/10/29
 */
public interface ToolReportService {

    /**
     * 创建汇报活动(发起人自动 OWNER;填报人/接收人批量加入)
     */
    Long create(ToolReportCreateReq req);

    /**
     * 我相关的汇报活动(含我在其中的角色与提交状态)
     */
    List<ToolReportResp> myActivities();

    /**
     * 活动详情(成员;含全部填报人的提交状态)
     */
    ToolReportResp detail(Long id);

    /**
     * 读取某人的月报:草稿仅本人,已提交全成员可读
     */
    ToolReportEntryResp entry(Long id, Long entryUserId);

    /**
     * 保存我的月报(submit=false 存草稿,true 提交;提交记录迟交标记)
     */
    void saveEntry(Long id, ToolReportEntryReq req);

    /**
     * 月报评论列表(升序,含楼中楼关系)
     */
    List<ToolReportCommentResp> comments(Long id, Long entryUserId);

    /**
     * 发表评论/回复(仅成员;写入被回复人与活动发起人的站内信)
     */
    Long comment(Long id, Long entryUserId, ToolReportCommentReq req);

    /**
     * 添加成员(仅发起人;角色 2 填报 / 3 接收)
     */
    void addMembers(Long id, List<Long> userIds, Integer role);

    /**
     * 移除成员(仅发起人;不能移除自己;被移除人的月报保留)
     */
    void removeMember(Long id, Long memberUserId);

    /**
     * 删除活动(仅发起人,级联删成员/月报/评论)
     */
    void delete(Long id);
}
