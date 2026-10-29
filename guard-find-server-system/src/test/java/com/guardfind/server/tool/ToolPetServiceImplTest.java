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

package com.guardfind.server.tool;

import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.exception.AppBizException;
import com.guardfind.server.tool.mapper.ToolPetLogMapper;
import com.guardfind.server.tool.mapper.ToolPetMapper;
import com.guardfind.server.tool.model.entity.ToolPetDO;
import com.guardfind.server.tool.model.entity.ToolPetLogDO;
import com.guardfind.server.tool.model.req.ToolPetLogReq;
import com.guardfind.server.tool.service.impl.ToolPetServiceImpl;
import com.guardfind.server.support.ContainerlessTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 宠物成长记录单测:归属校验、日志增删、越权防护。
 */
class ToolPetServiceImplTest extends ContainerlessTestBase {

    private ToolPetMapper petMapper;
    private ToolPetLogMapper logMapper;
    private ToolPetServiceImpl service;

    @BeforeEach
    void setUp() {
        petMapper = mock(ToolPetMapper.class);
        logMapper = mock(ToolPetLogMapper.class);
        service = new ToolPetServiceImpl(petMapper, logMapper);
    }

    private ToolPetDO pet(Long id, Long userId) {
        ToolPetDO pet = new ToolPetDO();
        pet.setId(id);
        pet.setUserId(userId);
        pet.setName("毛毛");
        pet.setSpecies("cat");
        return pet;
    }

    private ToolPetLogReq logReq(String type, String content) {
        ToolPetLogReq req = new ToolPetLogReq();
        req.setLogType(type);
        req.setContent(content);
        req.setLogDate(LocalDate.of(2026, 10, 29));
        return req;
    }

    @Test
    @DisplayName("添加日志:主人身份通过,落库带 petId 和 userId")
    void addLogOwned() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(petMapper.selectById(1L)).thenReturn(pet(1L, 7L));
            when(logMapper.insert(any(ToolPetLogDO.class))).thenAnswer(inv -> {
                ToolPetLogDO saved = inv.getArgument(0);
                saved.setId(10L);
                return 1;
            });
            Long id = service.addLog(1L, logReq("weight", "体重 4.2kg"));
            assertEquals(10L, id);
            verify(logMapper).insert(any(ToolPetLogDO.class));
        }
    }

    @Test
    @DisplayName("非主人添加日志返回 NOT_FOUND,不触库")
    void addLogRejectsNonOwner() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(99L);
            when(petMapper.selectById(1L)).thenReturn(pet(1L, 7L));
            AppBizException ex = assertThrows(AppBizException.class, () -> service
                .addLog(1L, logReq("weight", "test")));
            assertTrue(AppBizException.NOT_FOUND.equals(ex.getCode()));
            verify(logMapper, never()).insert(any(ToolPetLogDO.class));
        }
    }

    @Test
    @DisplayName("删除日志:仅删除本人宠物下的日志")
    void deleteLogScoped() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(petMapper.selectById(1L)).thenReturn(pet(1L, 7L));
            service.deleteLog(1L, 10L);
            verify(logMapper).delete(any());
        }
    }

    @Test
    @DisplayName("删除宠物:级联删日志")
    void deleteCascadesLogs() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(petMapper.selectById(1L)).thenReturn(pet(1L, 7L));
            service.delete(1L);
            verify(logMapper).delete(any());
            verify(petMapper).deleteById(1L);
        }
    }

    private static void assertEquals(long expected, long actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
