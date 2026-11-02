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

import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.property.mapper.PropertyMapper;
import com.guardfind.server.property.model.entity.PropertyDO;
import com.guardfind.server.property.model.req.PropertyReq;
import com.guardfind.server.property.service.impl.PropertyServiceImpl;
import com.guardfind.server.support.ContainerlessTestBase;
import top.continew.starter.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 房产房源单测:归属校验、状态流转、越权防护。
 */
class PropertyServiceImplTest extends ContainerlessTestBase {

    private PropertyMapper propertyMapper;
    private AppUserMapper appUserMapper;
    private PropertyServiceImpl service;

    @BeforeEach
    void setUp() {
        propertyMapper = mock(PropertyMapper.class);
        appUserMapper = mock(AppUserMapper.class);
        service = new PropertyServiceImpl(propertyMapper, appUserMapper, mock(com.guardfind.server.property.service.PropertyInteractionService.class));
    }

    private PropertyDO property(Long id, Long userId, int status) {
        PropertyDO property = new PropertyDO();
        property.setId(id);
        property.setUserId(userId);
        property.setTitle("精装两房");
        property.setStatus(status);
        return property;
    }

    private PropertyReq req() {
        PropertyReq req = new PropertyReq();
        req.setTitle("精装两房");
        req.setPropertyType("house");
        req.setPrice(new BigDecimal("3500"));
        req.setPriceType("rent");
        req.setAddress("test");
        req.setStatus(2);
        return req;
    }

    @Test
    @DisplayName("创建:落库带当前用户 ID,默认草稿")
    void createSetsUserId() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(propertyMapper.insert(any(PropertyDO.class))).thenAnswer(inv -> {
                PropertyDO saved = inv.getArgument(0);
                saved.setId(1L);
                return 1;
            });
            Long id = service.create(7L, req());
            assertEquals(1L, id);
            ArgumentCaptor<PropertyDO> captor = ArgumentCaptor.forClass(PropertyDO.class);
            verify(propertyMapper).insert(captor.capture());
            assertEquals(7L, captor.getValue().getUserId());
        }
    }

    @Test
    @DisplayName("编辑他人房源返回错误,不触库")
    void updateRejectsNonOwner() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(propertyMapper.selectById(5L)).thenReturn(property(5L, 99L, 2));
            assertThrows(BusinessException.class, () -> service.update(5L, 7L, req()));
            verify(propertyMapper, never()).updateById(any(PropertyDO.class));
        }
    }

    @Test
    @DisplayName("上架/下架:仅本人可操作")
    void updateStatusOwnerOnly() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(propertyMapper.selectById(5L)).thenReturn(property(5L, 7L, 2));
            service.updateStatus(5L, 7L, 3);
            ArgumentCaptor<PropertyDO> captor = ArgumentCaptor.forClass(PropertyDO.class);
            verify(propertyMapper).updateById(captor.capture());
            assertEquals(3, captor.getValue().getStatus());
        }
    }

    @Test
    @DisplayName("删除他人房源返回错误")
    void deleteRejectsNonOwner() {
        try (var ignored = mockStatic(AppUserContextHolder.class)) {
            when(AppUserContextHolder.getUserIdRequired()).thenReturn(7L);
            when(propertyMapper.selectById(5L)).thenReturn(property(5L, 99L, 2));
            assertThrows(BusinessException.class, () -> service.delete(5L, 7L));
            verify(propertyMapper, never()).deleteById(any(Long.class));
        }
    }

    private static void assertEquals(long expected, long actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
