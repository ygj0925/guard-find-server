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
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.ledger.service.LedgerGroupService;
import com.guardfind.server.tool.mapper.*;
import com.guardfind.server.tool.model.entity.*;
import com.guardfind.server.tool.service.impl.ToolGoalServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ToolGoalServiceImplTest {
    @Test
    @SuppressWarnings("unchecked")
    void unlinkWritesExplicitNullWithExpectedAssociationCondition() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), ToolTaskDO.class);
        var tasks = mock(ToolTaskMapper.class);
        var krs = mock(ToolKeyResultMapper.class);
        var kr = new ToolKeyResultDO(); kr.setId(10L); kr.setUserId(7L);
        var task = new ToolTaskDO(); task.setId(20L); task.setKrId(10L);
        when(krs.selectById(10L)).thenReturn(kr);
        when(tasks.selectById(20L)).thenReturn(task);
        var service = new ToolGoalServiceImpl(mock(ToolGoalMapper.class), mock(ToolKrLogMapper.class), tasks,
            mock(AppUserMapper.class), krs, mock(LedgerGroupService.class));
        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            service.unlinkTask(10L, 20L);
        }
        var update = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(tasks).update(isNull(), update.capture());
        assertThat(update.getValue().getSqlSet()).contains("kr_id=");
        assertThat(update.getValue().getParamNameValuePairs()).containsValue(null);
        assertThat(update.getValue().getSqlSegment()).contains("id", "kr_id");
        verify(tasks, never()).updateById(any(ToolTaskDO.class));
    }

    @Test
    void loadsLinkedTasksOnceForAllKeyResults() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, ToolGoalDO.class);
        TableInfoHelper.initTableInfo(assistant, ToolKeyResultDO.class);
        TableInfoHelper.initTableInfo(assistant, ToolTaskDO.class);
        var goals = mock(ToolGoalMapper.class); var tasks = mock(ToolTaskMapper.class); var krs = mock(ToolKeyResultMapper.class);
        var goal = new ToolGoalDO(); goal.setId(1L); goal.setPeriod("2026-Q4");
        var first = kr(10L); var second = kr(11L);
        var task = new ToolTaskDO(); task.setKrId(10L); task.setStatus(3);
        when(goals.selectList(any())).thenReturn(List.of(goal));
        when(krs.selectList(any())).thenReturn(List.of(first, second));
        when(tasks.selectList(any())).thenReturn(List.of(task));
        var service = new ToolGoalServiceImpl(goals, mock(ToolKrLogMapper.class), tasks, mock(AppUserMapper.class), krs, mock(LedgerGroupService.class));
        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            var results = service.list(null).get(0).getKeyResults();
            assertThat(results.get(0).getTaskTotal()).isEqualTo(1L);
            assertThat(results.get(0).getTaskDone()).isEqualTo(1L);
            assertThat(results.get(1).getTaskTotal()).isZero();
        }
        verify(tasks, times(1)).selectList(any());
    }

    private ToolKeyResultDO kr(long id) {
        var kr = new ToolKeyResultDO(); kr.setId(id); kr.setGoalId(1L);
        kr.setTargetValue(BigDecimal.TEN); kr.setCurrentValue(BigDecimal.ZERO); kr.setStatus(1);
        return kr;
    }
}
