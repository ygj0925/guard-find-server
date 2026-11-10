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

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.exception.AppBizException;
import com.guardfind.server.support.ContainerlessTestBase;
import com.guardfind.server.tool.mapper.ToolItemMapper;
import com.guardfind.server.tool.model.entity.ToolItemDO;
import com.guardfind.server.tool.model.req.ToolItemReq;
import com.guardfind.server.tool.service.impl.ToolItemServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.continew.starter.core.exception.BusinessException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ToolItemServiceImpl} 单测:归属校验、数量非负与并发比较、版本冲突、归档与分页。
 *
 * @author guardfind
 * @since 2026/11/06
 */
class ToolItemServiceImplTest extends ContainerlessTestBase {

    private ToolItemMapper mapper;
    private ToolItemServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), ToolItemDO.class);
        mapper = mock(ToolItemMapper.class);
        service = new ToolItemServiceImpl(mapper);
    }

    private static ToolItemDO item(Long id, Long userId, int quantity, int archived, int version) {
        ToolItemDO record = new ToolItemDO();
        record.setId(id);
        record.setUserId(userId);
        record.setName("牛奶");
        record.setLocation("冰箱");
        record.setQuantity(quantity);
        record.setArchived(archived);
        record.setVersion(version);
        return record;
    }

    private static ToolItemReq req(String name, String location, Integer quantity, Integer version) {
        ToolItemReq req = new ToolItemReq();
        req.setName(name);
        req.setLocation(location);
        req.setQuantity(quantity);
        req.setVersion(version);
        return req;
    }

    @Test
    @DisplayName("新增:归属当前用户、默认数量 1、版本 1、未归档")
    void createOwnsRecordWithDefaults() {
        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            service.create(req("  螺丝刀  ", "工具箱", null, null));

            ArgumentCaptor<ToolItemDO> captor = ArgumentCaptor.forClass(ToolItemDO.class);
            verify(mapper).insert(captor.capture());
            assertEquals(7L, captor.getValue().getUserId());
            assertEquals("螺丝刀", captor.getValue().getName());
            assertEquals("工具箱", captor.getValue().getLocation());
            assertEquals(1, captor.getValue().getQuantity());
            assertEquals(0, captor.getValue().getArchived());
            assertEquals(1, captor.getValue().getVersion());
        }
    }

    @Test
    @DisplayName("新增数量为负直接拒绝,不落库")
    void createRejectsNegativeQuantity() {
        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);

            assertThrows(BusinessException.class, () -> service.create(req("电池", "抽屉", -1, null)));
            verify(mapper, never()).insert(any(ToolItemDO.class));
        }
    }

    @Test
    @DisplayName("修改他人物品返回 NOT_FOUND,不执行更新")
    void updateRejectsForeignRecord() {
        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            when(mapper.selectById(5L)).thenReturn(item(5L, 8L, 2, 0, 1));

            AppBizException ex = assertThrows(AppBizException.class, () -> service.update(5L, req("改", "柜子", 2, 1)));
            assertEquals(AppBizException.NOT_FOUND, ex.getCode());
            verify(mapper, never()).update(any(), any());
        }
    }

    @Test
    @DisplayName("版本不一致返回冲突,条件更新带版本与归属并自增版本")
    void updateDetectsVersionConflict() {
        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            when(mapper.selectById(5L)).thenReturn(item(5L, 7L, 2, 0, 3));
            when(mapper.update(isNull(), any())).thenReturn(0);

            AppBizException ex = assertThrows(AppBizException.class, () -> service.update(5L, req("改", "柜子", 4, 2)));
            assertEquals(AppBizException.STATE_CONFLICT, ex.getCode());

            @SuppressWarnings("unchecked") ArgumentCaptor<LambdaUpdateWrapper<ToolItemDO>> captor = ArgumentCaptor
                .forClass(LambdaUpdateWrapper.class);
            verify(mapper).update(isNull(), captor.capture());
            String sql = captor.getValue().getSqlSegment();
            assertTrue(sql.contains("version ="), sql);
            assertTrue(sql.contains("user_id ="), sql);
            assertTrue(captor.getValue().getSqlSet().contains("version = version + 1"), captor.getValue().getSqlSet());
        }
    }

    @Test
    @DisplayName("扣减超过现有数量直接拒绝,不发起更新")
    void changeQuantityRejectsNegativeResult() {
        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            when(mapper.selectById(5L)).thenReturn(item(5L, 7L, 2, 0, 1));

            assertThrows(BusinessException.class, () -> service.changeQuantity(5L, -3));
            verify(mapper, never()).update(any(), any());
        }
    }

    @Test
    @DisplayName("数量调整按当前值做条件更新,并发变化返回冲突")
    void changeQuantityUsesCompareAndSet() {
        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            when(mapper.selectById(5L)).thenReturn(item(5L, 7L, 2, 0, 1));
            when(mapper.update(isNull(), any())).thenReturn(1);

            service.changeQuantity(5L, -1);
            @SuppressWarnings("unchecked") ArgumentCaptor<LambdaUpdateWrapper<ToolItemDO>> captor = ArgumentCaptor
                .forClass(LambdaUpdateWrapper.class);
            verify(mapper).update(isNull(), captor.capture());
            String sql = captor.getValue().getSqlSegment();
            assertTrue(sql.contains("quantity ="), sql);
            assertTrue(sql.contains("user_id ="), sql);
            assertTrue(captor.getValue().getSqlSet().contains("version = version + 1"));

            when(mapper.update(isNull(), any())).thenReturn(0);
            AppBizException ex = assertThrows(AppBizException.class, () -> service.changeQuantity(5L, -1));
            assertEquals(AppBizException.STATE_CONFLICT, ex.getCode());
        }
    }

    @Test
    @DisplayName("归档切换只更新本人记录")
    void toggleArchiveIsScopedToOwner() {
        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            when(mapper.selectById(5L)).thenReturn(item(5L, 7L, 2, 0, 1));

            service.toggleArchive(5L);

            @SuppressWarnings("unchecked") ArgumentCaptor<LambdaUpdateWrapper<ToolItemDO>> captor = ArgumentCaptor
                .forClass(LambdaUpdateWrapper.class);
            verify(mapper).update(isNull(), captor.capture());
            assertTrue(captor.getValue().getSqlSet().contains("archived = 1 - archived"));
            assertTrue(captor.getValue().getSqlSegment().contains("user_id ="));
        }
    }

    @Test
    @DisplayName("删除他人物品返回 NOT_FOUND 且不删除")
    void deleteRejectsForeignRecord() {
        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            when(mapper.selectById(5L)).thenReturn(item(5L, 8L, 2, 0, 1));

            assertThrows(AppBizException.class, () -> service.delete(5L));
            verify(mapper, never()).deleteById(any(Long.class));
        }
    }

    @Test
    @DisplayName("分页:按归档与位置过滤、关键词搜多字段、位置排序且页大小有上限")
    void pageFiltersAndBoundsSize() {
        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            Page<ToolItemDO> result = new Page<>(1, 50);
            result.setRecords(List.of(item(9L, 7L, 3, 0, 1)));
            result.setTotal(1);
            when(mapper.selectPage(any(), any())).thenReturn(result);

            var resp = service.page(0, 999, "  牛奶  ", " 冰箱 ", false);

            assertEquals(1, resp.getTotal());
            assertEquals(3, resp.getList().get(0).getQuantity());

            @SuppressWarnings("unchecked") ArgumentCaptor<Page<ToolItemDO>> pageCaptor = ArgumentCaptor
                .forClass(Page.class);
            @SuppressWarnings("unchecked") ArgumentCaptor<LambdaQueryWrapper<ToolItemDO>> queryCaptor = ArgumentCaptor
                .forClass(LambdaQueryWrapper.class);
            verify(mapper).selectPage(pageCaptor.capture(), queryCaptor.capture());
            assertEquals(1, pageCaptor.getValue().getCurrent());
            assertEquals(50, pageCaptor.getValue().getSize());
            String sql = queryCaptor.getValue().getSqlSegment();
            assertTrue(sql.contains("archived"), sql);
            assertTrue(sql.contains("location"), sql);
            assertTrue(sql.contains("ORDER BY location ASC, name ASC, id ASC"), sql);
            var params = queryCaptor.getValue().getParamNameValuePairs();
            assertTrue(params.containsValue("%牛奶%"), params.toString());
            assertTrue(params.containsValue("%冰箱%"), params.toString());
        }
    }
}
