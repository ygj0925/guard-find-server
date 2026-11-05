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
import com.guardfind.server.tool.mapper.ToolNoteMapper;
import com.guardfind.server.tool.model.entity.ToolNoteDO;
import com.guardfind.server.tool.model.req.ToolNoteReq;
import com.guardfind.server.tool.service.impl.ToolNoteServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.continew.starter.core.exception.BusinessException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * {@link ToolNoteServiceImpl} 单测:归属校验、版本冲突、置顶/归档切换与分页过滤。
 *
 * @author guardfind
 * @since 2026/11/06
 */
class ToolNoteServiceImplTest extends ContainerlessTestBase {

    private ToolNoteMapper mapper;
    private ToolNoteServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), ToolNoteDO.class);
        mapper = mock(ToolNoteMapper.class);
        service = new ToolNoteServiceImpl(mapper);
    }

    private static ToolNoteDO note(Long id, Long userId, String title, int pinned, int archived, int version) {
        ToolNoteDO record = new ToolNoteDO();
        record.setId(id);
        record.setUserId(userId);
        record.setTitle(title);
        record.setContent("正文");
        record.setPinned(pinned);
        record.setArchived(archived);
        record.setVersion(version);
        return record;
    }

    private static ToolNoteReq req(String title, String content, Integer version) {
        ToolNoteReq req = new ToolNoteReq();
        req.setTitle(title);
        req.setContent(content);
        req.setVersion(version);
        return req;
    }

    @Test
    @DisplayName("新建:归属当前用户、版本 1、默认未置顶未归档")
    void createOwnsRecordAndStartsAtVersionOne() {
        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            service.create(req("  周末采购  ", "牛奶", null));

            ArgumentCaptor<ToolNoteDO> captor = ArgumentCaptor.forClass(ToolNoteDO.class);
            verify(mapper).insert(captor.capture());
            assertEquals(7L, captor.getValue().getUserId());
            assertEquals("周末采购", captor.getValue().getTitle());
            assertEquals("牛奶", captor.getValue().getContent());
            assertEquals(0, captor.getValue().getPinned());
            assertEquals(0, captor.getValue().getArchived());
            assertEquals(1, captor.getValue().getVersion());
        }
    }

    @Test
    @DisplayName("修改他人便笺返回 NOT_FOUND,不执行更新")
    void updateRejectsForeignRecord() {
        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            when(mapper.selectById(5L)).thenReturn(note(5L, 8L, "别人的", 0, 0, 1));

            AppBizException ex = assertThrows(AppBizException.class, () -> service.update(5L, req("改", "", 1)));
            assertEquals(AppBizException.NOT_FOUND, ex.getCode());
            verify(mapper, never()).update(any(), any());
        }
    }

    @Test
    @DisplayName("修改缺版本号直接拒绝")
    void updateRequiresVersion() {
        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            when(mapper.selectById(5L)).thenReturn(note(5L, 7L, "我的", 0, 0, 3));

            assertThrows(BusinessException.class, () -> service.update(5L, req("改", "", null)));
            verify(mapper, never()).update(any(), any());
        }
    }

    @Test
    @DisplayName("版本不一致返回冲突,条件更新只命中同版本同归属")
    void updateDetectsVersionConflict() {
        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            when(mapper.selectById(5L)).thenReturn(note(5L, 7L, "我的", 0, 0, 3));
            when(mapper.update(isNull(), any())).thenReturn(0);

            AppBizException ex = assertThrows(AppBizException.class, () -> service.update(5L, req("改", "", 2)));
            assertEquals(AppBizException.STATE_CONFLICT, ex.getCode());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<LambdaUpdateWrapper<ToolNoteDO>> captor = ArgumentCaptor
                .forClass(LambdaUpdateWrapper.class);
            verify(mapper).update(isNull(), captor.capture());
            String sql = captor.getValue().getSqlSegment();
            assertTrue(sql.contains("version ="), sql);
            assertTrue(sql.contains("user_id ="), sql);
            assertTrue(captor.getValue().getSqlSet().contains("version = version + 1"), captor.getValue()
                .getSqlSet());
        }
    }

    @Test
    @DisplayName("置顶/归档切换只更新本人记录")
    void togglesAreScopedToOwner() {
        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            when(mapper.selectById(5L)).thenReturn(note(5L, 7L, "我的", 0, 0, 1));

            service.togglePin(5L);
            service.toggleArchive(5L);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<LambdaUpdateWrapper<ToolNoteDO>> captor = ArgumentCaptor
                .forClass(LambdaUpdateWrapper.class);
            verify(mapper, org.mockito.Mockito.times(2)).update(isNull(), captor.capture());
            assertTrue(captor.getAllValues().get(0).getSqlSet().contains("pinned = 1 - pinned"));
            assertTrue(captor.getAllValues().get(0).getSqlSegment().contains("user_id ="));
            assertTrue(captor.getAllValues().get(1).getSqlSet().contains("archived = 1 - archived"));
        }
    }

    @Test
    @DisplayName("删除他人便笺返回 NOT_FOUND 且不删除")
    void deleteRejectsForeignRecord() {
        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            when(mapper.selectById(5L)).thenReturn(note(5L, 8L, "别人的", 0, 0, 1));

            assertThrows(AppBizException.class, () -> service.delete(5L));
            verify(mapper, never()).deleteById(any(Long.class));
        }
    }

    @Test
    @DisplayName("分页:按归档过滤、关键词搜标题或正文、置顶排序且页大小有上限")
    void pageFiltersAndBoundsSize() {
        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            Page<ToolNoteDO> result = new Page<>(1, 50);
            result.setRecords(List.of(note(9L, 7L, "置顶", 1, 0, 1)));
            result.setTotal(1);
            when(mapper.selectPage(any(), any())).thenReturn(result);

            var resp = service.page(0, 999, "  牛奶  ", false);

            assertEquals(1, resp.getTotal());
            assertTrue(resp.getList().get(0).getPinned());
            assertFalse(resp.getList().get(0).getArchived());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Page<ToolNoteDO>> pageCaptor = ArgumentCaptor.forClass(Page.class);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<LambdaQueryWrapper<ToolNoteDO>> queryCaptor = ArgumentCaptor
                .forClass(LambdaQueryWrapper.class);
            verify(mapper).selectPage(pageCaptor.capture(), queryCaptor.capture());
            assertEquals(1, pageCaptor.getValue().getCurrent());
            assertEquals(50, pageCaptor.getValue().getSize());
            String sql = queryCaptor.getValue().getSqlSegment();
            assertTrue(sql.contains("archived"), sql);
            assertTrue(sql.contains("COALESCE(update_time, create_time) DESC"), sql);
            assertTrue(queryCaptor.getValue().getParamNameValuePairs().containsValue("%牛奶%"));
        }
    }
}
