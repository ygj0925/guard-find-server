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
import com.guardfind.server.common.config.exception.GlobalExceptionHandler;
import com.guardfind.server.notification.enums.NotificationEventEnum;
import com.guardfind.server.notification.mapper.AppMessageMapper;
import com.guardfind.server.notification.mapper.PreferenceMapper;
import com.guardfind.server.notification.model.entity.AppMessageDO;
import com.guardfind.server.notification.model.entity.PreferenceDO;
import com.guardfind.server.notification.model.query.AppMessageQuery;
import com.guardfind.server.notification.model.req.PreferenceItemReq;
import com.guardfind.server.notification.model.req.PreferenceUpdateReq;
import com.guardfind.server.notification.service.AppMessageService;
import com.guardfind.server.notification.service.NotificationRealtimeService;
import com.guardfind.server.notification.service.NotificationSmsService;
import com.guardfind.server.support.ContainerlessTestBase;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotNull;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import top.continew.starter.core.exception.BadRequestException;
import top.continew.starter.extension.crud.model.query.PageQuery;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        TableInfoHelper
            .initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), PreferenceDO.class);
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
    @DisplayName("全部标记只更新一次本人全部未读消息，无 ID 批次或 500 上限，返回实际 650 条")
    void markAllReadUpdatesAllOwnUnreadMessages() {
        when(messageMapper.update(isNull(), any())).thenReturn(650);
        LocalDateTime before = LocalDateTime.now();

        assertThat(service.markAllRead(7L).getMarkedCount()).isEqualTo(650);

        LocalDateTime after = LocalDateTime.now();
        ArgumentCaptor<LambdaUpdateWrapper<AppMessageDO>> captor = updateCaptor();
        verify(messageMapper).update(isNull(), captor.capture());
        assertAllReadUpdate(captor.getValue(), before, after);
        verifyNoMoreInteractions(messageMapper);
        verifyNoInteractions(preferenceMapper);
    }

    @Test
    @DisplayName("全部标记更新零行时返回零，仍只执行一次 UPDATE，不预先查询")
    void markAllReadReturnsZeroFromSingleUpdate() {
        when(messageMapper.update(isNull(), any())).thenReturn(0);
        LocalDateTime before = LocalDateTime.now();

        assertThat(service.markAllRead(7L).getMarkedCount()).isZero();

        LocalDateTime after = LocalDateTime.now();
        ArgumentCaptor<LambdaUpdateWrapper<AppMessageDO>> captor = updateCaptor();
        verify(messageMapper).update(isNull(), captor.capture());
        assertAllReadUpdate(captor.getValue(), before, after);
        verifyNoMoreInteractions(messageMapper);
        verifyNoInteractions(preferenceMapper);
    }

    @Test
    @DisplayName("有效项之后的 null 偏好项在任何 Mapper 访问前被业务 400 拒绝")
    void updatePreferenceRejectsNullItemBeforeMapperAccess() {
        PreferenceUpdateReq req = preferenceRequest(preferenceItem("CASE_LEAD", true, false), null);

        BadRequestException exception = assertThrows(BadRequestException.class, () -> service
            .updatePreference(req, 7L));

        assertThat(exception).hasMessage("通知偏好项不能为空");
        assertBadRequestCode(exception);
        verifyNoInteractions(messageMapper, preferenceMapper);
    }

    @Test
    @DisplayName("CASE_LEAD 与 case_lead 归一后重复，在任何 Mapper 访问前被业务 400 拒绝")
    void updatePreferenceRejectsCaseInsensitiveDuplicatesBeforeMapperAccess() {
        PreferenceUpdateReq req = preferenceRequest(preferenceItem("CASE_LEAD", true, false), preferenceItem("case_lead", false, true));

        BadRequestException exception = assertThrows(BadRequestException.class, () -> service
            .updatePreference(req, 7L));

        assertThat(exception).hasMessage("通知类型 [CASE_LEAD] 不能重复");
        assertBadRequestCode(exception);
        verifyNoInteractions(messageMapper, preferenceMapper);
    }

    @Test
    @DisplayName("DTO 校验拒绝列表 null 元素，合法项的 null 短信开关不违反约束")
    void preferenceRequestValidatesNullElementsButAllowsNullSms() {
        PreferenceItemReq validItem = preferenceItem("CASE_LEAD", true, null);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            assertThat(validator.validate(preferenceRequest(validItem))).isEmpty();
            var violations = validator.validate(preferenceRequest(validItem, null));
            assertThat(violations).hasSize(1);
            var violation = violations.iterator().next();
            assertThat(violation.getPropertyPath().toString()).isEqualTo("items[1].<list element>");
            assertThat(violation.getConstraintDescriptor().getAnnotation()).isInstanceOf(NotNull.class);
            assertThat(violation.getMessage()).isEqualTo("通知偏好项不能为空");
        }
        verifyNoInteractions(messageMapper, preferenceMapper);
    }

    @Test
    @DisplayName("新增合法偏好时归一类型、允许关闭非强制站内信，null 短信存为 false")
    void updatePreferenceInsertsNullSmsAsFalse() {
        when(preferenceMapper.selectList(any())).thenReturn(List.of());
        LocalDateTime before = LocalDateTime.now();

        service.updatePreference(preferenceRequest(preferenceItem(" case_lead ", false, null)), 7L);

        LocalDateTime after = LocalDateTime.now();
        ArgumentCaptor<PreferenceDO> captor = ArgumentCaptor.forClass(PreferenceDO.class);
        verify(preferenceMapper).selectList(any());
        verify(preferenceMapper).insert(captor.capture());
        PreferenceDO inserted = captor.getValue();
        assertThat(inserted.getUserId()).isEqualTo(7L);
        assertThat(inserted.getType()).isEqualTo("CASE_LEAD");
        assertThat(inserted.getInboxEnabled()).isFalse();
        assertThat(inserted.getSmsEnabled()).isFalse();
        assertThat(inserted.getCreateUser()).isEqualTo(7L);
        assertThat(inserted.getUpdateUser()).isEqualTo(7L);
        assertThat(inserted.getCreateTime()).isBetween(before, after);
        assertThat(inserted.getUpdateTime()).isEqualTo(inserted.getCreateTime());
        verifyNoMoreInteractions(preferenceMapper);
        verifyNoInteractions(messageMapper);
    }

    @Test
    @DisplayName("已有偏好原短信开启时，合法请求的 null 短信仍覆盖为 false")
    void updatePreferenceOverwritesEnabledSmsWithFalseForNull() {
        PreferenceDO existing = new PreferenceDO();
        existing.setId(91L);
        existing.setUserId(7L);
        existing.setType("CASE_LEAD");
        existing.setInboxEnabled(true);
        existing.setSmsEnabled(true);
        when(preferenceMapper.selectList(any())).thenReturn(List.of(existing));
        LocalDateTime before = LocalDateTime.now();

        service.updatePreference(preferenceRequest(preferenceItem("case_lead", false, null)), 7L);

        LocalDateTime after = LocalDateTime.now();
        @SuppressWarnings("unchecked") ArgumentCaptor<LambdaUpdateWrapper<PreferenceDO>> captor = ArgumentCaptor
            .forClass(LambdaUpdateWrapper.class);
        verify(preferenceMapper).selectList(any());
        verify(preferenceMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<PreferenceDO> wrapper = captor.getValue();
        String sql = wrapper.getSqlSegment();
        assertThat(boundValue(wrapper, sql, "id")).isEqualTo(91L);
        assertThat(boundValue(wrapper, sql, "user_id")).isEqualTo(7L);
        assertThat(sql).doesNotContain(" OR ");
        String sqlSet = wrapper.getSqlSet();
        assertThat(boundValue(wrapper, sqlSet, "inbox_enabled")).isEqualTo(false);
        assertThat(boundValue(wrapper, sqlSet, "sms_enabled")).isEqualTo(false);
        assertThat(boundValue(wrapper, sqlSet, "update_user")).isEqualTo(7L);
        assertThat((LocalDateTime)boundValue(wrapper, sqlSet, "update_time")).isBetween(before, after);
        verifyNoMoreInteractions(preferenceMapper);
        verifyNoInteractions(messageMapper);
    }

    @ParameterizedTest
    @EnumSource(value = NotificationEventEnum.class, names = {"CASE_APPROVED", "CASE_REJECTED", "SYSTEM"})
    @DisplayName("强制通知仍不能关闭站内信，不写入偏好")
    void updatePreferenceStillRejectsDisablingForcedInbox(NotificationEventEnum event) {
        when(preferenceMapper.selectList(any())).thenReturn(List.of());

        BadRequestException exception = assertThrows(BadRequestException.class, () -> service
            .updatePreference(preferenceRequest(preferenceItem(event.name(), false, null)), 7L));

        assertThat(exception).hasMessage("「%s」为重要结果通知，不支持关闭站内信", event.getLabel());
        assertBadRequestCode(exception);
        verify(preferenceMapper).selectList(any());
        verifyNoMoreInteractions(preferenceMapper);
        verifyNoInteractions(messageMapper);
    }

    @Test
    @DisplayName("举报结果与系统消息均跳转到 /message")
    void systemEventsBuildMessagePath() {
        assertThat(NotificationEventEnum.REPORT_RESULT.buildPath(7L)).isEqualTo("/message");
        assertThat(NotificationEventEnum.SYSTEM.buildPath(7L)).isEqualTo("/message");
    }

    private static PreferenceItemReq preferenceItem(String type, boolean inbox, Boolean sms) {
        PreferenceItemReq item = new PreferenceItemReq();
        item.setType(type);
        item.setInboxEnabled(inbox);
        item.setSmsEnabled(sms);
        return item;
    }

    private static PreferenceUpdateReq preferenceRequest(PreferenceItemReq... items) {
        PreferenceUpdateReq req = new PreferenceUpdateReq();
        req.setItems(Arrays.asList(items));
        return req;
    }

    private static void assertBadRequestCode(BadRequestException exception) {
        var response = new GlobalExceptionHandler()
            .handleBadRequestException(exception, new MockHttpServletRequest("PUT", "/app/notification/preference"));
        assertThat(new ObjectMapper().valueToTree(response).path("code").asText()).isEqualTo("400");
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

    private static void assertAllReadUpdate(LambdaUpdateWrapper<AppMessageDO> wrapper,
                                            LocalDateTime before,
                                            LocalDateTime after) {
        assertUnreadScope(wrapper);
        String sql = wrapper.getSqlSegment();
        assertThat(sql).doesNotContain(" IN ", "LIMIT", "ORDER BY").doesNotContainPattern("\\bid\\b");
        assertThat(wrapper.getParamNameValuePairs()).hasSize(6);
        assertReadAudit(wrapper);
        assertThat((LocalDateTime)boundValue(wrapper, wrapper.getSqlSet(), "read_time")).isBetween(before, after);
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
        assertReadAudit(wrapper);
    }

    private static void assertReadAudit(LambdaUpdateWrapper<AppMessageDO> wrapper) {
        String sqlSet = wrapper.getSqlSet();
        assertThat(boundValue(wrapper, sqlSet, "read_status")).isEqualTo(true);
        assertThat(boundValue(wrapper, sqlSet, "update_user")).isEqualTo(7L);
        Object readTime = boundValue(wrapper, sqlSet, "read_time");
        assertThat(readTime).isInstanceOf(LocalDateTime.class);
        assertThat(boundValue(wrapper, sqlSet, "update_time")).isEqualTo(readTime);
    }

    private static Object boundValue(AbstractWrapper<?, ?, ?> wrapper, String sql, String column) {
        Matcher matcher = Pattern.compile("\\b" + column + "\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.([^}]+)\\}")
            .matcher(sql);
        assertThat(matcher.find()).as("SQL 必须绑定 %s: %s", column, sql).isTrue();
        assertThat(wrapper.getParamNameValuePairs()).containsKey(matcher.group(1));
        return wrapper.getParamNameValuePairs().get(matcher.group(1));
    }
}
