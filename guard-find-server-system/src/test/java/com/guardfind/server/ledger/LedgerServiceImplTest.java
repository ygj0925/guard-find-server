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

package com.guardfind.server.ledger;

import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.exception.AppBizException;
import com.guardfind.server.ledger.mapper.LedgerRecordMapper;
import com.guardfind.server.ledger.model.entity.LedgerRecordDO;
import com.guardfind.server.ledger.model.req.LedgerRecordReq;
import com.guardfind.server.ledger.service.impl.LedgerServiceImpl;
import com.guardfind.server.support.ContainerlessTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.continew.starter.core.exception.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LedgerServiceImpl} 单测：登录校验、分类与类型匹配、越权防护。
 * 纯 Mockito，无容器（见 ContainerlessTestBase）。
 *
 * @author guardfind
 * @since 2026/10/28
 */
class LedgerServiceImplTest extends ContainerlessTestBase {

    private LedgerRecordMapper mapper;
    private LedgerServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = org.mockito.Mockito.mock(LedgerRecordMapper.class);
        service = new LedgerServiceImpl(mapper);
    }

    private static LedgerRecordReq req(int type, String amount, String category) {
        LedgerRecordReq req = new LedgerRecordReq();
        req.setType(type);
        req.setAmount(new BigDecimal(amount));
        req.setCategory(category);
        req.setRecordDate(LocalDate.of(2026, 10, 28));
        req.setRemark("午餐");
        return req;
    }

    @Test
    @DisplayName("未登录创建直接 401，不触库")
    void createRequiresLogin() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired())
                .thenThrow(new AppBizException(AppBizException.UNAUTHORIZED, "未登录"));
            AppBizException ex = assertThrows(AppBizException.class, () -> service.create(req(1, "10.00", "food")));
            assertEquals(AppBizException.UNAUTHORIZED, ex.getCode());
            verify(mapper, never()).insert(any(LedgerRecordDO.class));
        }
    }

    @Test
    @DisplayName("分类与收支类型不匹配被拒绝")
    void createRejectsMismatchedCategory() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            // salary 是收入分类，type=1 支出时应被拒绝
            assertThrows(BusinessException.class, () -> service.create(req(1, "10.00", "salary")));
            verify(mapper, never()).insert(any(LedgerRecordDO.class));
        }
    }

    @Test
    @DisplayName("创建成功：落库带当前用户 ID，方向与分类一致")
    void createInsertsWithUserId() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(mapper.insert(any(LedgerRecordDO.class))).thenAnswer(invocation -> {
                LedgerRecordDO record = invocation.getArgument(0);
                record.setId(99L);
                return 1;
            });
            Long id = service.create(req(2, "8000.00", "salary"));
            assertEquals(99L, id);
            ArgumentCaptor<LedgerRecordDO> captor = ArgumentCaptor.forClass(LedgerRecordDO.class);
            verify(mapper).insert(captor.capture());
            assertEquals(7L, captor.getValue().getUserId());
            assertEquals(2, captor.getValue().getType());
            assertEquals("salary", captor.getValue().getCategory());
        }
    }

    @Test
    @DisplayName("修改他人记录与不存在记录同话术，不泄露存在性")
    void updateRejectsForeignRecord() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            LedgerRecordDO foreign = new LedgerRecordDO();
            foreign.setId(5L);
            foreign.setUserId(8L);
            when(mapper.selectById(5L)).thenReturn(foreign);
            AppBizException ex = assertThrows(AppBizException.class, () -> service.update(5L, req(1, "10.00", "food")));
            assertEquals(AppBizException.NOT_FOUND, ex.getCode());
            verify(mapper, never()).updateById(any(LedgerRecordDO.class));
        }
    }

    @Test
    @DisplayName("删除本人记录放行")
    void deleteOwnedRecord() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            LedgerRecordDO own = new LedgerRecordDO();
            own.setId(5L);
            own.setUserId(7L);
            when(mapper.selectById(5L)).thenReturn(own);
            service.delete(5L);
            verify(mapper).deleteById(5L);
        }
    }
}
