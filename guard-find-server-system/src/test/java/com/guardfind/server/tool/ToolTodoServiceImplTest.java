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
import com.guardfind.server.tool.mapper.ToolTodoMapper;
import com.guardfind.server.tool.model.entity.ToolTodoDO;
import com.guardfind.server.tool.model.req.ToolTodoReq;
import com.guardfind.server.tool.service.impl.ToolTodoServiceImpl;
import com.guardfind.server.support.ContainerlessTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ToolTodoServiceImpl} 单测:toggle 翻转、排序口径(未完成在前/无截止日最后)、归属校验。
 *
 * @author guardfind
 * @since 2026/10/28
 */
class ToolTodoServiceImplTest extends ContainerlessTestBase {

    private ToolTodoMapper mapper;
    private ToolTodoServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(ToolTodoMapper.class);
        service = new ToolTodoServiceImpl(mapper);
    }

    private static ToolTodoDO todo(Long id, Long userId, String title, int done, LocalDate dueDate) {
        ToolTodoDO record = new ToolTodoDO();
        record.setId(id);
        record.setUserId(userId);
        record.setTitle(title);
        record.setDone(done);
        record.setDueDate(dueDate);
        return record;
    }

    @Test
    @DisplayName("toggle:未完成翻成完成,完成翻回未完成")
    void toggleFlipsState() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            ToolTodoDO record = todo(5L, 7L, "买礼物", 0, null);
            when(mapper.selectById(5L)).thenReturn(record);
            service.toggle(5L);
            assertEquals(1, record.getDone());
            verify(mapper).updateById(record);

            service.toggle(5L);
            assertEquals(0, record.getDone());
        }
    }

    @Test
    @DisplayName("列表排序:未完成(有截止日升序)在前,无截止日随后,已完成最后")
    void listOrdering() {
        LocalDate today = LocalDate.now();
        // 乱序给(可变列表,service 会原地 sort):已完成、无截止日、远截止日、近截止日
        List<ToolTodoDO> records = new java.util.ArrayList<>(List
            .of(todo(1L, 7L, "已完成", 1, null), todo(2L, 7L, "无截止", 0, null), todo(3L, 7L, "远", 0, today
                .plusDays(9)), todo(4L, 7L, "近", 0, today.plusDays(1))));
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(mapper.selectList(any())).thenReturn(records);
            var list = service.list();
            assertEquals("近", list.get(0).getTitle());
            assertEquals("远", list.get(1).getTitle());
            assertEquals("无截止", list.get(2).getTitle());
            assertEquals("已完成", list.get(3).getTitle());
            assertTrue(list.get(3).getDone());
            assertFalse(list.get(0).getDone());
        }
    }

    @Test
    @DisplayName("修改他人待办返回 NOT_FOUND")
    void updateRejectsForeignRecord() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(mapper.selectById(5L)).thenReturn(todo(5L, 8L, "别人的", 0, null));
            ToolTodoReq req = new ToolTodoReq();
            req.setTitle("改成我的");
            AppBizException ex = assertThrows(AppBizException.class, () -> service.update(5L, req));
            assertEquals(AppBizException.NOT_FOUND, ex.getCode());
        }
    }
}
