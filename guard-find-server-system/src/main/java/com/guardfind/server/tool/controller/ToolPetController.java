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
import top.continew.starter.ratelimiter.annotation.RateLimiter;
import top.continew.starter.ratelimiter.enums.LimitType;

import com.guardfind.server.tool.model.req.ToolPetLogReq;
import com.guardfind.server.tool.model.req.ToolPetReq;
import com.guardfind.server.tool.model.resp.ToolPetLogResp;
import com.guardfind.server.tool.model.resp.ToolPetResp;
import com.guardfind.server.tool.service.ToolPetService;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 宠物成长记录 API(C 端)
 */
@Tag(name = "宠物成长记录 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/tool/pet")
public class ToolPetController {

    private final ToolPetService petService;

    @Operation(summary = "创建宠物档案")
    @RateLimiter(name = "APP:PET:CREATE", rate = 10, interval = 1, unit = TimeUnit.HOURS, type = LimitType.IP)
    @PostMapping
    public Long create(@RequestBody @Valid ToolPetReq req) {
        return petService.create(req);
    }

    @Operation(summary = "修改宠物档案", description = "仅主人可修改")
    @Parameter(name = "id", description = "宠物 ID", required = true)
    @PutMapping("/{id}")
    public void update(@PathVariable Long id, @RequestBody @Valid ToolPetReq req) {
        petService.update(id, req);
    }

    @Operation(summary = "删除宠物", description = "级联删成长日志")
    @Parameter(name = "id", description = "宠物 ID", required = true)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        petService.delete(id);
    }

    @Operation(summary = "我的宠物列表")
    @GetMapping("/list")
    public List<ToolPetResp> list() {
        return petService.list();
    }

    @Operation(summary = "添加成长日志", description = "log_type: weight/vaccine/medical/milestone/daily")
    @Parameter(name = "petId", description = "宠物 ID", required = true)
    @PostMapping("/{petId}/log")
    public Long addLog(@PathVariable Long petId, @RequestBody @Valid ToolPetLogReq req) {
        return petService.addLog(petId, req);
    }

    @Operation(summary = "删除成长日志")
    @Parameter(name = "petId", description = "宠物 ID", required = true)
    @Parameter(name = "logId", description = "日志 ID", required = true)
    @DeleteMapping("/{petId}/log/{logId}")
    public void deleteLog(@PathVariable Long petId, @PathVariable Long logId) {
        petService.deleteLog(petId, logId);
    }

    @Operation(summary = "成长日志列表", description = "logType 可空=全部;按日期倒序")
    @Parameter(name = "petId", description = "宠物 ID", required = true)
    @Parameter(name = "logType", description = "记录类型,可空=全部")
    @GetMapping("/{petId}/log")
    public List<ToolPetLogResp> logs(@PathVariable Long petId, @RequestParam(required = false) String logType) {
        return petService.logs(petId, logType);
    }
}
