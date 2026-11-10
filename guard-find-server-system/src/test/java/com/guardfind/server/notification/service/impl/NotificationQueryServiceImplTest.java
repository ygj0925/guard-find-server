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

package com.guardfind.server.notification.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardfind.server.notification.mapper.AppMessageMapper;
import com.guardfind.server.notification.mapper.PreferenceMapper;
import com.guardfind.server.notification.model.entity.AppMessageDO;
import com.guardfind.server.notification.model.query.AppMessageQuery;
import com.guardfind.server.notification.service.AppMessageService;
import com.guardfind.server.notification.service.NotificationRealtimeService;
import com.guardfind.server.notification.service.NotificationSmsService;
import com.guardfind.server.support.ContainerlessTestBase;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.continew.starter.core.exception.BadRequestException;
import top.continew.starter.extension.crud.model.query.PageQuery;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 消息读权限及未读过滤单元测试：捕获 Mapper Wrapper，不验证真实数据库或事务一致性。
 */
class NotificationQueryServiceImplTest extends ContainerlessTestBase {

    private AppMessageMapper messageMapper;
    private PreferenceMapper preferenceMapper;
    private NotificationQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper
            .initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), AppMessageDO.class);
        messageMapper = mock(AppMessageMapper.class);
        preferenceMapper = mock(PreferenceMapper.class);
        service = new NotificationQueryServiceImpl(messageMapper, preferenceMapper, mock(AppMessageService.class));
    }

    @Test
    @DisplayName("未读分页与未读计数使用相同的接收人和未读条件及绑定值")
    void unreadPageAndCountUseSameFilters() {
        AppMessageServiceImpl appMessageService = new AppMessageServiceImpl(messageMapper, preferenceMapper, mock(NotificationRealtimeService.class), mock(NotificationSmsService.class), new ObjectMapper());
        service = new NotificationQueryServiceImpl(messageMapper, preferenceMapper, appMessageService);
        when(messageMapper.selectCount(any())).thenReturn(3L);
        when(messageMapper.selectPage(any(), any())).thenReturn(new Page<AppMessageDO>(1, 10));
        AppMessageQuery query = new AppMessageQuery();
        query.setOnlyUnread(true);

        assertThat(service.countUnread(7L).getUnreadCount()).isEqualTo(3L);
        service.page(query, new PageQuery(1, 10), 7L);

        ArgumentCaptor<LambdaQueryWrapper<AppMessageDO>> countCaptor = queryCaptor();
        ArgumentCaptor<LambdaQueryWrapper<AppMessageDO>> pageCaptor = queryCaptor();
        verify(messageMapper).selectCount(countCaptor.capture());
        verify(messageMapper).selectPage(any(), pageCaptor.capture());
        LambdaQueryWrapper<AppMessageDO> countWrapper = countCaptor.getValue();
        LambdaQueryWrapper<AppMessageDO> pageWrapper = pageCaptor.getValue();
        assertUnreadScope(countWrapper);
        assertUnreadScope(pageWrapper);
        assertThat(pageWrapper.getExpression().getNormal().getSqlSegment()).isEqualTo(countWrapper.getSqlSegment());
        assertThat(pageWrapper.getParamNameValuePairs()).isEqualTo(countWrapper.getParamNameValuePairs());
        assertThat(pageWrapper.getSqlSegment()).doesNotContain("category");
    }

    @Test
    @DisplayName("分页绑定本人、未读和去除空白后的合法分组，并按时间和 ID 倒序")
    void pageScopesReceiverUnreadAndCategory() {
        Page<AppMessageDO> result = new Page<>(2, 20);
        result.setRecords(List.of(message(91L, 7L, false)));
        result.setTotal(1);
        when(messageMapper.selectPage(any(), any())).thenReturn(result);
        AppMessageQuery query = new AppMessageQuery();
        query.setOnlyUnread(true);
        query.setCategory("  LEAD  ");

        var response = service.page(query, new PageQuery(2, 20), 7L);

        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getList()).extracting(item -> item.getId()).containsExactly(91L);
        ArgumentCaptor<LambdaQueryWrapper<AppMessageDO>> captor = queryCaptor();
        @SuppressWarnings("unchecked") ArgumentCaptor<Page<AppMessageDO>> pageCaptor = ArgumentCaptor
            .forClass(Page.class);
        verify(messageMapper).selectPage(pageCaptor.capture(), captor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(2);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(20);
        assertUnreadScope(captor.getValue());
        assertThat(boundValue(captor.getValue(), captor.getValue().getSqlSegment(), "category")).isEqualTo("LEAD");
        assertThat(captor.getValue().getSqlSegment()).contains("ORDER BY create_time DESC,id DESC");
    }

    @Test
    @DisplayName("空分页参数默认查询本人全部消息，不附加未读或分类条件")
    void pageDefaultsToAllOwnMessages() {
        when(messageMapper.selectPage(any(), any())).thenReturn(new Page<AppMessageDO>(1, 10));

        assertThat(service.page(null, null, 7L).getList()).isEmpty();

        ArgumentCaptor<LambdaQueryWrapper<AppMessageDO>> captor = queryCaptor();
        @SuppressWarnings("unchecked") ArgumentCaptor<Page<AppMessageDO>> pageCaptor = ArgumentCaptor
            .forClass(Page.class);
        verify(messageMapper).selectPage(pageCaptor.capture(), captor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(10);
        String sql = captor.getValue().getSqlSegment();
        assertThat(boundValue(captor.getValue(), sql, "receiver_id")).isEqualTo(7L);
        assertThat(sql).doesNotContain("read_status", "category");
        assertThat(captor.getValue().getParamNameValuePairs()).hasSize(1);
    }

    @Test
    @DisplayName("onlyUnread=false 与空白分类不限制已读状态或消息分组")
    void pageIgnoresFalseUnreadAndBlankCategory() {
        when(messageMapper.selectPage(any(), any())).thenReturn(new Page<AppMessageDO>(1, 10));
        AppMessageQuery query = new AppMessageQuery();
        query.setOnlyUnread(false);
        query.setCategory("   ");

        service.page(query, null, 7L);

        ArgumentCaptor<LambdaQueryWrapper<AppMessageDO>> captor = queryCaptor();
        verify(messageMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(boundValue(captor.getValue(), sql, "receiver_id")).isEqualTo(7L);
        assertThat(sql).doesNotContain("read_status", "category");
        assertThat(captor.getValue().getParamNameValuePairs()).hasSize(1);
    }

    @Test
    @DisplayName("非法分类返回业务异常，不访问消息 Mapper")
    void pageRejectsInvalidCategoryBeforeQuery() {
        AppMessageQuery query = new AppMessageQuery();
        query.setCategory("  UNKNOWN  ");

        BadRequestException exception = assertThrows(BadRequestException.class, () -> service.page(query, null, 7L));

        assertThat(exception).hasMessage("消息分组 [UNKNOWN] 不合法");
        verifyNoInteractions(messageMapper);
    }

    @Test
    @DisplayName("不存在和他人消息返回相同异常及话术，包含他人已读消息，均不写入")
    void markReadHidesMissingAndForeignMessages() {
        when(messageMapper.selectById(91L)).thenReturn(null, message(91L, 8L, false), message(91L, 8L, true));

        BadRequestException missing = assertThrows(BadRequestException.class, () -> service.markRead(91L, 7L));
        BadRequestException foreignUnread = assertThrows(BadRequestException.class, () -> service.markRead(91L, 7L));
        BadRequestException foreignRead = assertThrows(BadRequestException.class, () -> service.markRead(91L, 7L));

        assertThat(missing).hasMessage("消息不存在");
        assertThat(foreignUnread).isExactlyInstanceOf(missing.getClass()).hasMessage(missing.getMessage());
        assertThat(foreignRead).isExactlyInstanceOf(missing.getClass()).hasMessage(missing.getMessage());
        verify(messageMapper, times(3)).selectById(91L);
        verifyNoMoreInteractions(messageMapper);
    }

    @Test
    @DisplayName("单条标记更新同时约束消息 ID、本人接收人和未读状态")
    void markReadUpdatesOnlyOwnUnreadMessage() {
        when(messageMapper.selectById(91L)).thenReturn(message(91L, 7L, false));
        when(messageMapper.update(isNull(), any())).thenReturn(1);

        service.markRead(91L, 7L);

        ArgumentCaptor<LambdaUpdateWrapper<AppMessageDO>> captor = updateCaptor();
        verify(messageMapper).selectById(91L);
        verify(messageMapper).update(isNull(), captor.capture());
        assertReadUpdate(captor.getValue(), List.of(91L));
        verifyNoMoreInteractions(messageMapper);
    }

    @Test
    @DisplayName("本人已读消息重复标记幂等，不产生更新")
    void markReadIsIdempotentForAlreadyReadMessage() {
        when(messageMapper.selectById(91L)).thenReturn(message(91L, 7L, true));

        service.markRead(91L, 7L);
        service.markRead(91L, 7L);

        verify(messageMapper, times(2)).selectById(91L);
        verifyNoMoreInteractions(messageMapper);
    }

    @Test
    @DisplayName("全部标记只选本人未读 ID，按最早时间限 500 条，更新仍约束本人和未读")
    void markAllReadUsesBoundedOwnUnreadBatch() {
        List<AppMessageDO> batch = LongStream.rangeClosed(1001, 1500).mapToObj(id -> message(id, 7L, false)).toList();
        when(messageMapper.selectList(any())).thenReturn(batch);
        when(messageMapper.update(isNull(), any())).thenReturn(500);

        assertThat(service.markAllRead(7L).getMarkedCount()).isEqualTo(500);

        ArgumentCaptor<LambdaQueryWrapper<AppMessageDO>> selectCaptor = queryCaptor();
        ArgumentCaptor<LambdaUpdateWrapper<AppMessageDO>> updateCaptor = updateCaptor();
        verify(messageMapper).selectList(selectCaptor.capture());
        verify(messageMapper).update(isNull(), updateCaptor.capture());
        assertUnreadScope(selectCaptor.getValue());
        assertThat(selectCaptor.getValue().getSqlSelect()).isEqualTo("id");
        assertThat(selectCaptor.getValue().getSqlSegment()).contains("ORDER BY create_time ASC").endsWith("LIMIT 500");
        assertReadUpdate(updateCaptor.getValue(), batch.stream().map(AppMessageDO::getId).toList());
        verifyNoMoreInteractions(messageMapper);
    }

    @Test
    @DisplayName("全部标记的未读批次为空时返回零，不产生更新")
    void markAllReadSkipsEmptyBatch() {
        when(messageMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.markAllRead(7L).getMarkedCount()).isZero();

        ArgumentCaptor<LambdaQueryWrapper<AppMessageDO>> captor = queryCaptor();
        verify(messageMapper).selectList(captor.capture());
        assertUnreadScope(captor.getValue());
        verifyNoMoreInteractions(messageMapper);
    }

    private static AppMessageDO message(Long id, Long receiverId, boolean readStatus) {
        AppMessageDO message = new AppMessageDO();
        message.setId(id);
        message.setReceiverId(receiverId);
        message.setReadStatus(readStatus);
        return message;
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<LambdaQueryWrapper<AppMessageDO>> queryCaptor() {
        return ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<LambdaUpdateWrapper<AppMessageDO>> updateCaptor() {
        return ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
    }

    private static void assertUnreadScope(AbstractWrapper<AppMessageDO, ?, ?> wrapper) {
        String sql = wrapper.getSqlSegment();
        assertThat(boundValue(wrapper, sql, "receiver_id")).isEqualTo(7L);
        assertThat(boundValue(wrapper, sql, "read_status")).isEqualTo(false);
        assertThat(sql).doesNotContain(" OR ");
    }

    private static void assertReadUpdate(LambdaUpdateWrapper<AppMessageDO> wrapper, List<Long> ids) {
        assertUnreadScope(wrapper);
        String sql = wrapper.getSqlSegment();
        Matcher idMatcher = Pattern.compile("\\bid\\s+IN\\s*\\(([^)]+)\\)").matcher(sql);
        assertThat(idMatcher.find()).as("更新 WHERE 必须限制本次消息 ID: %s", sql).isTrue();
        List<Object> boundIds = Pattern.compile("#\\{ew\\.paramNameValuePairs\\.([^}]+)\\}")
            .matcher(idMatcher.group(1))
            .results()
            .map(match -> wrapper.getParamNameValuePairs().get(match.group(1)))
            .toList();
        assertThat(boundIds).containsExactlyElementsOf(ids);
        String sqlSet = wrapper.getSqlSet();
        assertThat(boundValue(wrapper, sqlSet, "read_status")).isEqualTo(true);
        assertThat(boundValue(wrapper, sqlSet, "update_user")).isEqualTo(7L);
        Object readTime = boundValue(wrapper, sqlSet, "read_time");
        assertThat(readTime).isInstanceOf(LocalDateTime.class);
        assertThat(boundValue(wrapper, sqlSet, "update_time")).isEqualTo(readTime);
    }

    private static Object boundValue(AbstractWrapper<AppMessageDO, ?, ?> wrapper, String sql, String column) {
        Matcher matcher = Pattern.compile("\\b" + column + "\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.([^}]+)\\}")
            .matcher(sql);
        assertThat(matcher.find()).as("SQL 必须绑定 %s: %s", column, sql).isTrue();
        assertThat(wrapper.getParamNameValuePairs()).containsKey(matcher.group(1));
        return wrapper.getParamNameValuePairs().get(matcher.group(1));
    }
}
