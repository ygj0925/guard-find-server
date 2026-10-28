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
import com.guardfind.server.tool.mapper.ToolCountdownMapper;
import com.guardfind.server.tool.model.entity.ToolCountdownDO;
import com.guardfind.server.tool.model.req.ToolCountdownReq;
import com.guardfind.server.tool.model.resp.ToolCountdownResp;
import com.guardfind.server.tool.service.impl.ToolCountdownServiceImpl;
import com.guardfind.server.support.ContainerlessTestBase;
import top.continew.starter.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ToolCountdownServiceImpl} 单测:归属校验、daysLeft 口径、空白标题拒绝。
 *
 * @author guardfind
 * @since 2026/10/28
 */
class ToolCountdownServiceImplTest extends ContainerlessTestBase {

    private ToolCountdownMapper mapper;
    private ToolCountdownServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(ToolCountdownMapper.class);
        service = new ToolCountdownServiceImpl(mapper);
    }

    private static ToolCountdownReq req(String title, LocalDate targetDate) {
        ToolCountdownReq req = new ToolCountdownReq();
        req.setTitle(title);
        req.setTargetDate(targetDate);
        return req;
    }

    private static ToolCountdownDO record(Long id, Long userId, LocalDate targetDate) {
        ToolCountdownDO record = new ToolCountdownDO();
        record.setId(id);
        record.setUserId(userId);
        record.setTitle("事项");
        record.setTargetDate(targetDate);
        return record;
    }

    @Test
    @DisplayName("创建:trim 标题,落库带当前用户")
    void createTrimsTitle() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(mapper.insert(any(ToolCountdownDO.class))).thenAnswer(invocation -> {
                ToolCountdownDO saved = invocation.getArgument(0);
                saved.setId(11L);
                return 1;
            });
            Long id = service.create(req("  考研  ", LocalDate.of(2026, 12, 20)));
            assertEquals(11L, id);
            ArgumentCaptor<ToolCountdownDO> captor = ArgumentCaptor.forClass(ToolCountdownDO.class);
            verify(mapper).insert(captor.capture());
            assertEquals("考研", captor.getValue().getTitle());
            assertEquals(7L, captor.getValue().getUserId());
        }
    }

    @Test
    @DisplayName("空白标题创建被拒绝,不触库")
    void createRejectsBlankTitle() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            assertThrows(BusinessException.class, () -> service.create(req("   ", LocalDate.now())));
            verify(mapper, never()).insert(any(ToolCountdownDO.class));
        }
    }

    @Test
    @DisplayName("删除他人记录返回 NOT_FOUND,不泄露存在性")
    void deleteRejectsForeignRecord() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(mapper.selectById(5L)).thenReturn(record(5L, 8L, LocalDate.now()));
            AppBizException ex = assertThrows(AppBizException.class, () -> service.delete(5L));
            assertEquals(AppBizException.NOT_FOUND, ex.getCode());
            verify(mapper, never()).deleteById(any(Long.class));
        }
    }

    @Test
    @DisplayName("daysLeft 口径:未来为正、今天为 0、过去为负")
    void daysLeftCalculation() {
        LocalDate today = LocalDate.now();
        List<ToolCountdownDO> records = List.of(record(1L, 7L, today
            .plusDays(10)), record(2L, 7L, today), record(3L, 7L, today.minusDays(3)));
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(mapper.selectList(any())).thenReturn(records);
            List<ToolCountdownResp> list = service.list();
            assertEquals(10L, list.get(0).getDaysLeft());
            assertEquals(0L, list.get(1).getDaysLeft());
            assertEquals(-3L, list.get(2).getDaysLeft());
        }
    }
}
