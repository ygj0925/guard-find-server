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

package com.guardfind.server.ledger.service;

import com.guardfind.server.ledger.model.entity.LedgerGroupDO;
import com.guardfind.server.ledger.model.req.LedgerGroupReq;
import com.guardfind.server.ledger.model.req.LedgerJoinReq;
import com.guardfind.server.ledger.model.resp.LedgerGroupMemberResp;
import com.guardfind.server.ledger.model.resp.LedgerGroupResp;

import java.util.List;

/**
 * 记账家庭组业务接口(C 端)
 *
 * @author guardfind
 * @since 2026/10/29
 */
public interface LedgerGroupService {

    /**
     * 创建家庭组(创建者为组长)
     */
    LedgerGroupResp create(LedgerGroupReq req);

    /**
     * 我加入的组列表(含角色/成员数;组长额外返回邀请码)
     */
    List<LedgerGroupResp> myGroups();

    /**
     * 凭邀请码加入(已是成员幂等返回)
     */
    LedgerGroupResp join(LedgerJoinReq req);

    /**
     * 组长重新生成邀请码
     */
    LedgerGroupResp regenerateInviteCode(Long groupId);

    /**
     * 组成员列表(昵称/头像)
     */
    List<LedgerGroupMemberResp> members(Long groupId);

    /**
     * 组长移除成员(不能移除自己)
     */
    void removeMember(Long groupId, Long memberUserId);

    /**
     * 成员退出(组长不能退出,只能解散或转让——一期仅解散)
     */
    void quit(Long groupId);

    /**
     * 组长解散组:成员关系清空,组内账单归还原主个人账本(group_id 置空)
     */
    void dissolve(Long groupId);

    /**
     * 校验当前用户是组成员;不是则抛 NOT_FOUND(不泄露组存在性)
     *
     * @return 组 DO
     */
    LedgerGroupDO requireMembership(Long groupId);
}
