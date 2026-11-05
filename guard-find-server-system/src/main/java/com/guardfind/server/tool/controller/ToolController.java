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

package com.guardfind.server.tool.controller;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.guardfind.server.tool.model.req.ToolCountdownReq;
import com.guardfind.server.tool.model.req.ToolItemReq;
import com.guardfind.server.tool.model.req.ToolNoteReq;
import com.guardfind.server.tool.model.req.ToolTodoReq;
import com.guardfind.server.tool.model.resp.ToolCountdownResp;
import com.guardfind.server.tool.model.resp.ToolItemResp;
import com.guardfind.server.tool.model.resp.ToolNoteResp;
import com.guardfind.server.tool.model.resp.ToolTodoResp;
import com.guardfind.server.tool.service.ToolCountdownService;
import com.guardfind.server.tool.service.ToolItemService;
import com.guardfind.server.tool.service.ToolNoteService;
import com.guardfind.server.tool.service.ToolTodoService;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.util.List;

/**
 * 工具箱小工具 API(C 端):倒数日 + 待办。
 * 数据都是个人数据,登录态由 AppUserContextHolder 判定。
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Tag(name = "工具箱小工具 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/tool")
public class ToolController {

    private final ToolCountdownService countdownService;
    private final ToolTodoService todoService;
    private final ToolNoteService noteService;
    private final ToolItemService itemService;

    // ==================== 倒数日 ====================

    @Operation(summary = "新建倒数日")
    @PostMapping("/countdown")
    public Long createCountdown(@RequestBody @Valid ToolCountdownReq req) {
        return countdownService.create(req);
    }

    @Operation(summary = "修改倒数日", description = "仅本人记录可修改")
    @Parameter(name = "id", description = "记录 ID", required = true)
    @PutMapping("/countdown/{id}")
    public void updateCountdown(@PathVariable Long id, @RequestBody @Valid ToolCountdownReq req) {
        countdownService.update(id, req);
    }

    @Operation(summary = "删除倒数日", description = "仅本人记录可删除")
    @Parameter(name = "id", description = "记录 ID", required = true)
    @DeleteMapping("/countdown/{id}")
    public void deleteCountdown(@PathVariable Long id) {
        countdownService.delete(id);
    }

    @Operation(summary = "倒数日列表", description = "按目标日期升序;daysLeft 服务端算好(负数=已过)")
    @GetMapping("/countdown/list")
    public List<ToolCountdownResp> listCountdown() {
        return countdownService.list();
    }

    // ==================== 待办 ====================

    @Operation(summary = "新建待办")
    @PostMapping("/todo")
    public Long createTodo(@RequestBody @Valid ToolTodoReq req) {
        return todoService.create(req);
    }

    @Operation(summary = "修改待办", description = "仅本人记录可修改")
    @Parameter(name = "id", description = "记录 ID", required = true)
    @PutMapping("/todo/{id}")
    public void updateTodo(@PathVariable Long id, @RequestBody @Valid ToolTodoReq req) {
        todoService.update(id, req);
    }

    @Operation(summary = "切换待办完成状态", description = "完成/未完成互切")
    @Parameter(name = "id", description = "记录 ID", required = true)
    @PostMapping("/todo/{id}/toggle")
    public void toggleTodo(@PathVariable Long id) {
        todoService.toggle(id);
    }

    @Operation(summary = "删除待办", description = "仅本人记录可删除")
    @Parameter(name = "id", description = "记录 ID", required = true)
    @DeleteMapping("/todo/{id}")
    public void deleteTodo(@PathVariable Long id) {
        todoService.delete(id);
    }

    @Operation(summary = "待办列表", description = "未完成在前(截止日升序),已完成在后")
    @GetMapping("/todo/list")
    public List<ToolTodoResp> listTodo() {
        return todoService.list();
    }

    // ==================== 灵感便笺 ====================

    @Operation(summary = "新建便笺")
    @PostMapping("/note")
    public Long createNote(@RequestBody @Valid ToolNoteReq req) {
        return noteService.create(req);
    }

    @Operation(summary = "修改便笺", description = "需携带 version，版本不一致返回冲突;仅本人记录可修改")
    @Parameter(name = "id", description = "记录 ID", required = true)
    @PutMapping("/note/{id}")
    public void updateNote(@PathVariable Long id, @RequestBody @Valid ToolNoteReq req) {
        noteService.update(id, req);
    }

    @Operation(summary = "切换便笺置顶", description = "仅本人记录可操作")
    @Parameter(name = "id", description = "记录 ID", required = true)
    @PostMapping("/note/{id}/pin")
    public void toggleNotePin(@PathVariable Long id) {
        noteService.togglePin(id);
    }

    @Operation(summary = "切换便笺归档", description = "仅本人记录可操作")
    @Parameter(name = "id", description = "记录 ID", required = true)
    @PostMapping("/note/{id}/archive")
    public void toggleNoteArchive(@PathVariable Long id) {
        noteService.toggleArchive(id);
    }

    @Operation(summary = "删除便笺", description = "仅本人记录可删除")
    @Parameter(name = "id", description = "记录 ID", required = true)
    @DeleteMapping("/note/{id}")
    public void deleteNote(@PathVariable Long id) {
        noteService.delete(id);
    }

    @Operation(summary = "便笺分页", description = "置顶优先,其余按更新时间倒序;keyword 同时匹配标题与正文")
    @GetMapping("/note/list")
    public PageResp<ToolNoteResp> listNote(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "false") boolean archived) {
        return noteService.page(page, size, keyword, archived);
    }

    // ==================== 物品收纳 ====================

    @Operation(summary = "新增物品")
    @PostMapping("/item")
    public Long createItem(@RequestBody @Valid ToolItemReq req) {
        return itemService.create(req);
    }

    @Operation(summary = "修改物品", description = "需携带 version，版本不一致返回冲突;仅本人记录可修改")
    @Parameter(name = "id", description = "记录 ID", required = true)
    @PutMapping("/item/{id}")
    public void updateItem(@PathVariable Long id, @RequestBody @Valid ToolItemReq req) {
        itemService.update(id, req);
    }

    @Operation(summary = "调整物品数量", description = "delta 为增量，扣减不能低于 0;并发下按当前值条件更新")
    @Parameter(name = "id", description = "记录 ID", required = true)
    @Parameter(name = "delta", description = "数量增量，可为负", required = true)
    @PostMapping("/item/{id}/quantity")
    public void changeItemQuantity(@PathVariable Long id, @RequestParam int delta) {
        itemService.changeQuantity(id, delta);
    }

    @Operation(summary = "切换物品归档", description = "仅本人记录可操作")
    @Parameter(name = "id", description = "记录 ID", required = true)
    @PostMapping("/item/{id}/archive")
    public void toggleItemArchive(@PathVariable Long id) {
        itemService.toggleArchive(id);
    }

    @Operation(summary = "删除物品", description = "仅本人记录可删除")
    @Parameter(name = "id", description = "记录 ID", required = true)
    @DeleteMapping("/item/{id}")
    public void deleteItem(@PathVariable Long id) {
        itemService.delete(id);
    }

    @Operation(summary = "物品分页", description = "按位置、名称排序;keyword 匹配名称/位置/备注，location 精确筛选所在位置")
    @GetMapping("/item/list")
    public PageResp<ToolItemResp> listItem(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String location,
                                           @RequestParam(defaultValue = "false") boolean archived) {
        return itemService.page(page, size, keyword, location, archived);
    }
}
