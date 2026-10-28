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

package com.guardfind.server.ledger.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guardfind.server.ledger.model.req.LedgerGroupReq;
import com.guardfind.server.ledger.model.req.LedgerJoinReq;
import com.guardfind.server.ledger.model.resp.LedgerGroupMemberResp;
import com.guardfind.server.ledger.model.resp.LedgerGroupResp;
import com.guardfind.server.ledger.service.LedgerGroupService;

import java.util.List;

/**
 * 记账家庭组 API(C 端):创建/加入/成员管理/解散。
 * 口径:每人只能改删自己的账单;组长不能退出只能解散;解散后组内账单回归个人账本。
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Tag(name = "记账家庭组 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/ledger/group")
public class LedgerGroupController {

    private final LedgerGroupService groupService;

    @Operation(summary = "创建家庭组", description = "创建者为组长,自动生成邀请码")
    @PostMapping
    public LedgerGroupResp create(@RequestBody @Valid LedgerGroupReq req) {
        return groupService.create(req);
    }

    @Operation(summary = "我的家庭组列表", description = "含角色与成员数;邀请码仅组长可见")
    @GetMapping("/list")
    public List<LedgerGroupResp> myGroups() {
        return groupService.myGroups();
    }

    @Operation(summary = "凭邀请码加入", description = "已是成员幂等返回;每组成员上限 12 人")
    @PostMapping("/join")
    public LedgerGroupResp join(@RequestBody @Valid LedgerJoinReq req) {
        return groupService.join(req);
    }

    @Operation(summary = "重新生成邀请码", description = "仅组长;旧码立即失效")
    @Parameter(name = "id", description = "组 ID", required = true)
    @PostMapping("/{id}/invite-code")
    public LedgerGroupResp regenerateInviteCode(@PathVariable Long id) {
        return groupService.regenerateInviteCode(id);
    }

    @Operation(summary = "组成员列表", description = "含昵称头像;仅组成员可查")
    @Parameter(name = "id", description = "组 ID", required = true)
    @GetMapping("/{id}/members")
    public List<LedgerGroupMemberResp> members(@PathVariable Long id) {
        return groupService.members(id);
    }

    @Operation(summary = "移除成员", description = "仅组长;不能移除自己")
    @Parameter(name = "id", description = "组 ID", required = true)
    @DeleteMapping("/{id}/member/{memberUserId}")
    public void removeMember(@PathVariable Long id, @PathVariable Long memberUserId) {
        groupService.removeMember(id, memberUserId);
    }

    @Operation(summary = "退出家庭组", description = "成员可退出;组长不能退出(只能解散)")
    @Parameter(name = "id", description = "组 ID", required = true)
    @DeleteMapping("/{id}/quit")
    public void quit(@PathVariable Long id) {
        groupService.quit(id);
    }

    @Operation(summary = "解散家庭组", description = "仅组长;组内账单归还原主个人账本")
    @Parameter(name = "id", description = "组 ID", required = true)
    @DeleteMapping("/{id}")
    public void dissolve(@PathVariable Long id) {
        groupService.dissolve(id);
    }
}
