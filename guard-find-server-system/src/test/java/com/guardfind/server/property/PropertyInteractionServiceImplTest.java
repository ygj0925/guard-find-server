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

package com.guardfind.server.property;

import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.notification.service.AppMessageService;
import com.guardfind.server.property.mapper.PropertyBookingMapper;
import com.guardfind.server.property.mapper.PropertyFavoriteMapper;
import com.guardfind.server.property.mapper.PropertyMapper;
import com.guardfind.server.property.mapper.PropertyViewHistoryMapper;
import com.guardfind.server.property.model.entity.PropertyBookingDO;
import com.guardfind.server.property.model.entity.PropertyDO;
import com.guardfind.server.property.model.entity.PropertyFavoriteDO;
import com.guardfind.server.property.model.entity.PropertyViewHistoryDO;
import com.guardfind.server.property.model.req.PropertyBookingReq;
import com.guardfind.server.property.service.impl.PropertyInteractionServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.guardfind.server.support.ContainerlessTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.continew.starter.core.exception.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 房源互动单测:收藏切换、浏览历史 upsert、预约状态机与越权防护。
 */
@DisplayName("房源互动")
class PropertyInteractionServiceImplTest extends ContainerlessTestBase {

    private PropertyMapper propertyMapper;
    private PropertyFavoriteMapper favoriteMapper;
    private PropertyViewHistoryMapper viewHistoryMapper;
    private PropertyBookingMapper bookingMapper;
    private AppUserMapper appUserMapper;
    private AppMessageService messageService;
    private PropertyInteractionServiceImpl service;

    @BeforeEach
    void setUp() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper
            .initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"), PropertyBookingDO.class);
        propertyMapper = mock(PropertyMapper.class);
        favoriteMapper = mock(PropertyFavoriteMapper.class);
        viewHistoryMapper = mock(PropertyViewHistoryMapper.class);
        bookingMapper = mock(PropertyBookingMapper.class);
        when(bookingMapper.update(org.mockito.ArgumentMatchers.isNull(), any())).thenReturn(1);
        appUserMapper = mock(AppUserMapper.class);
        messageService = mock(AppMessageService.class);
        service = new PropertyInteractionServiceImpl(propertyMapper, favoriteMapper, viewHistoryMapper, bookingMapper, appUserMapper, messageService);
    }

    private PropertyFavoriteDO favorite(long id, long userId, long propertyId) {
        PropertyFavoriteDO f = new PropertyFavoriteDO();
        f.setId(id);
        f.setUserId(userId);
        f.setPropertyId(propertyId);
        f.setCreateTime(LocalDateTime.now());
        return f;
    }

    private PropertyDO listedProperty(long id, long ownerId) {
        PropertyDO property = new PropertyDO();
        property.setId(id);
        property.setUserId(ownerId);
        property.setTitle("南向两居室");
        property.setStatus(2);
        property.setPrice(new BigDecimal("5000"));
        property.setPriceType("rent");
        return property;
    }

    private PropertyBookingReq bookingReq() {
        PropertyBookingReq req = new PropertyBookingReq();
        req.setBookingTime(LocalDateTime.now().plusHours(24));
        req.setContactName("张三");
        req.setContactPhone("13800000000");
        req.setRemark("希望下午看房");
        return req;
    }

    @Test
    @DisplayName("收藏切换:未收藏时插入并返回 true,已收藏时删除并返回 false")
    void toggleFavoriteInsertsThenDeletes() {
        when(propertyMapper.lockById(10L)).thenReturn(listedProperty(10L, 2L));
        when(favoriteMapper.selectOne(any())).thenReturn(null).thenReturn(favorite(77L, 1L, 10L));

        assertTrue(service.toggleFavorite(1L, 10L));
        assertFalse(service.toggleFavorite(1L, 10L));

        verify(favoriteMapper).insert(any(PropertyFavoriteDO.class));
        verify(favoriteMapper).deleteById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("浏览历史:首次插入,重复浏览刷新时间,异常静默不抛出")
    void recordViewUpsertsAndNeverThrows() {
        when(viewHistoryMapper.selectOne(any())).thenReturn(null).thenReturn(new PropertyViewHistoryDO());

        service.recordView(1L, 10L);
        verify(viewHistoryMapper).insert(any(PropertyViewHistoryDO.class));

        service.recordView(1L, 10L);
        verify(viewHistoryMapper).update(any(), any());

        // 下游异常不影响调用方
        when(viewHistoryMapper.selectOne(any())).thenThrow(new RuntimeException("db down"));
        service.recordView(1L, 10L);
    }

    @Test
    @DisplayName("预约校验:不能预约自己的房源/未上架不可预约/时间必须晚于当前")
    void createBookingValidations() {
        when(propertyMapper.lockById(10L)).thenReturn(listedProperty(10L, 2L));

        BusinessException own = assertThrows(BusinessException.class, () -> service
            .createBooking(2L, 10L, bookingReq()));
        assertTrue(own.getMessage().contains("自己的房源"));

        PropertyDO draft = listedProperty(10L, 2L);
        draft.setStatus(1);
        when(propertyMapper.lockById(10L)).thenReturn(draft);
        BusinessException notListed = assertThrows(BusinessException.class, () -> service
            .createBooking(1L, 10L, bookingReq()));
        assertTrue(notListed.getMessage().contains("不可预约"));

        when(propertyMapper.lockById(10L)).thenReturn(listedProperty(10L, 2L));
        PropertyBookingReq past = bookingReq();
        past.setBookingTime(LocalDateTime.now().minusHours(1));
        BusinessException expired = assertThrows(BusinessException.class, () -> service.createBooking(1L, 10L, past));
        assertTrue(expired.getMessage().contains("晚于当前时间"));

        verify(bookingMapper, never()).insert(any(PropertyBookingDO.class));
    }

    @Test
    @DisplayName("预约成功:落库待确认状态并站内信通知房东")
    void createBookingNotifiesOwner() {
        when(propertyMapper.lockById(10L)).thenReturn(listedProperty(10L, 2L));

        service.createBooking(1L, 10L, bookingReq());

        ArgumentCaptor<PropertyBookingDO> captor = ArgumentCaptor.forClass(PropertyBookingDO.class);
        verify(bookingMapper).insert(captor.capture());
        assertEquals(2L, captor.getValue().getOwnerId());
        assertEquals(1, captor.getValue().getStatus());
        verify(messageService).send(org.mockito.ArgumentMatchers.eq(2L), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("状态流转:预约人只能取消;房东确认/拒绝;仅待确认可处理")
    void updateBookingStatusTransitions() {
        PropertyBookingDO pending = new PropertyBookingDO();
        pending.setId(100L);
        pending.setPropertyId(10L);
        pending.setUserId(1L);
        pending.setOwnerId(2L);
        pending.setStatus(1);
        when(bookingMapper.selectById(100L)).thenReturn(pending);

        // 预约人不能确认
        BusinessException guestConfirm = assertThrows(BusinessException.class, () -> service
            .updateBookingStatus(1L, 100L, 2));
        assertTrue(guestConfirm.getMessage().contains("只能取消"));

        // 房东不能取消
        BusinessException ownerCancel = assertThrows(BusinessException.class, () -> service
            .updateBookingStatus(2L, 100L, 4));
        assertTrue(ownerCancel.getMessage().contains("确认或拒绝"));

        // 房东确认成功并通知预约人
        service.updateBookingStatus(2L, 100L, 2);
        ArgumentCaptor<PropertyBookingDO> captor = ArgumentCaptor.forClass(PropertyBookingDO.class);
        verify(bookingMapper).update(org.mockito.ArgumentMatchers.isNull(), any());
        verify(messageService).send(org.mockito.ArgumentMatchers.eq(1L), any(), any(), any(), any(), any());

        // 已处理的预约不能再操作
        pending.setStatus(2);
        BusinessException handled = assertThrows(BusinessException.class, () -> service
            .updateBookingStatus(2L, 100L, 3));
        assertTrue(handled.getMessage().contains("已处理"));
    }

    @Test
    @DisplayName("非预约相关用户操作预约视为不存在")
    void updateBookingStatusRejectsStranger() {
        PropertyBookingDO booking = new PropertyBookingDO();
        booking.setId(100L);
        booking.setUserId(1L);
        booking.setOwnerId(2L);
        booking.setStatus(1);
        when(bookingMapper.selectById(100L)).thenReturn(booking);

        assertThrows(BusinessException.class, () -> service.updateBookingStatus(99L, 100L, 2));
        verify(bookingMapper, never()).updateById(any(PropertyBookingDO.class));
    }

    @Test
    @DisplayName("预约列表:guest 查我预约的,owner 查我收到的")
    void bookingsSplitsByRole() {
        when(bookingMapper.selectPage(any(), any())).thenReturn(new Page<>());
        AppUserDO user = new AppUserDO();
        user.setId(1L);
        user.setNickname("张三");
        when(appUserMapper.selectByIds(any())).thenReturn(List.of(user));

        service.bookings(1L, "guest", null);
        service.bookings(1L, "owner", null);

        verify(bookingMapper, org.mockito.Mockito.times(2)).selectPage(any(), any());
    }
}
