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

package com.guardfind.server.property.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.notification.enums.NotificationEventEnum;
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
import com.guardfind.server.property.model.resp.PropertyBookingResp;
import com.guardfind.server.property.model.resp.PropertyResp;
import com.guardfind.server.property.service.PropertyInteractionService;
import com.guardfind.server.property.support.PropertyRespAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 房源互动实现。收藏/历史每用户每房源一行;预约走 guest(预约人)/owner(房东)双视角状态机。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyInteractionServiceImpl implements PropertyInteractionService {

    /** 预约状态 */
    private static final int BOOKING_PENDING = 1;
    private static final int BOOKING_CONFIRMED = 2;
    private static final int BOOKING_REJECTED = 3;
    private static final int BOOKING_CANCELED = 4;

    private static final String ROLE_OWNER = "owner";

    private final PropertyMapper propertyMapper;
    private final PropertyFavoriteMapper favoriteMapper;
    private final PropertyViewHistoryMapper viewHistoryMapper;
    private final PropertyBookingMapper bookingMapper;
    private final AppUserMapper appUserMapper;
    private final AppMessageService messageService;

    @Override
    public boolean toggleFavorite(Long userId, Long propertyId) {
        requireProperty(propertyId);
        PropertyFavoriteDO existing = favoriteMapper.selectOne(Wrappers.<PropertyFavoriteDO>lambdaQuery()
            .eq(PropertyFavoriteDO::getUserId, userId)
            .eq(PropertyFavoriteDO::getPropertyId, propertyId)
            .last("LIMIT 1"));
        if (existing != null) {
            favoriteMapper.deleteById(existing.getId());
            return false;
        }
        PropertyFavoriteDO favorite = new PropertyFavoriteDO();
        favorite.setUserId(userId);
        favorite.setPropertyId(propertyId);
        favorite.setCreateTime(LocalDateTime.now());
        favoriteMapper.insert(favorite);
        return true;
    }

    @Override
    public boolean isFavorited(Long userId, Long propertyId) {
        return favoriteMapper.selectCount(Wrappers.<PropertyFavoriteDO>lambdaQuery()
            .eq(PropertyFavoriteDO::getUserId, userId)
            .eq(PropertyFavoriteDO::getPropertyId, propertyId)) > 0;
    }

    @Override
    public PageResp<PropertyResp> favorites(Long userId, PageQuery pageQuery) {
        Page<PropertyFavoriteDO> page = favoriteMapper
            .selectPage(new Page<>(pageNum(pageQuery), pageSize(pageQuery)), Wrappers.<PropertyFavoriteDO>lambdaQuery()
                .eq(PropertyFavoriteDO::getUserId, userId)
                .orderByDesc(PropertyFavoriteDO::getCreateTime));
        return toPropertyPage(page.getRecords().stream().map(PropertyFavoriteDO::getPropertyId).toList(), page
            .getTotal());
    }

    @Override
    public PageResp<PropertyResp> history(Long userId, PageQuery pageQuery) {
        Page<PropertyViewHistoryDO> page = viewHistoryMapper
            .selectPage(new Page<>(pageNum(pageQuery), pageSize(pageQuery)), Wrappers
                .<PropertyViewHistoryDO>lambdaQuery()
                .eq(PropertyViewHistoryDO::getUserId, userId)
                .orderByDesc(PropertyViewHistoryDO::getViewTime));
        return toPropertyPage(page.getRecords().stream().map(PropertyViewHistoryDO::getPropertyId).toList(), page
            .getTotal());
    }

    @Override
    public void recordView(Long userId, Long propertyId) {
        try {
            PropertyViewHistoryDO existing = viewHistoryMapper.selectOne(Wrappers.<PropertyViewHistoryDO>lambdaQuery()
                .eq(PropertyViewHistoryDO::getUserId, userId)
                .eq(PropertyViewHistoryDO::getPropertyId, propertyId)
                .last("LIMIT 1"));
            if (existing != null) {
                viewHistoryMapper.update(null, Wrappers.<PropertyViewHistoryDO>update()
                    .eq("id", existing.getId())
                    .set("view_time", LocalDateTime.now()));
            } else {
                PropertyViewHistoryDO record = new PropertyViewHistoryDO();
                record.setUserId(userId);
                record.setPropertyId(propertyId);
                record.setViewTime(LocalDateTime.now());
                viewHistoryMapper.insert(record);
            }
        } catch (Exception e) {
            // 浏览历史失败不影响详情展示
            log.debug("[property] 记录浏览历史失败 userId={} propertyId={}: {}", userId, propertyId, e.getMessage());
        }
    }

    @Override
    public Long createBooking(Long userId, Long propertyId, PropertyBookingReq req) {
        PropertyDO property = requireProperty(propertyId);
        if (Objects.equals(property.getUserId(), userId)) {
            throw new BusinessException("不能预约自己的房源");
        }
        if (property.getStatus() != 2) {
            throw new BusinessException("该房源当前不可预约");
        }
        if (req.getBookingTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("预约时间必须晚于当前时间");
        }
        PropertyBookingDO booking = new PropertyBookingDO();
        booking.setPropertyId(propertyId);
        booking.setUserId(userId);
        booking.setOwnerId(property.getUserId());
        booking.setBookingTime(req.getBookingTime());
        booking.setContactName(StrUtil.trim(req.getContactName()));
        booking.setContactPhone(StrUtil.trim(req.getContactPhone()));
        booking.setRemark(StrUtil.trimToNull(req.getRemark()));
        booking.setStatus(BOOKING_PENDING);
        booking.setCreateTime(LocalDateTime.now());
        bookingMapper.insert(booking);

        // 站内信通知房东
        messageService.send(property.getUserId(), NotificationEventEnum.POST_COMMENTED, "PROPERTY_BOOKING", booking
            .getId(), "收到看房预约", "你的房源「" + property.getTitle() + "」有新的看房预约,点击查看");
        return booking.getId();
    }

    @Override
    public PageResp<PropertyBookingResp> bookings(Long userId, String role, PageQuery pageQuery) {
        boolean asOwner = ROLE_OWNER.equalsIgnoreCase(role);
        Page<PropertyBookingDO> page = bookingMapper
            .selectPage(new Page<>(pageNum(pageQuery), pageSize(pageQuery)), Wrappers.<PropertyBookingDO>lambdaQuery()
                .eq(asOwner, PropertyBookingDO::getOwnerId, userId)
                .eq(!asOwner, PropertyBookingDO::getUserId, userId)
                .orderByDesc(PropertyBookingDO::getCreateTime));
        return toBookingPage(page.getRecords(), page.getTotal());
    }

    @Override
    public void updateBookingStatus(Long userId, Long bookingId, Integer status) {
        PropertyBookingDO booking = bookingMapper.selectById(bookingId);
        if (booking == null) {
            throw new BusinessException("预约不存在");
        }
        boolean asOwner = Objects.equals(booking.getOwnerId(), userId);
        boolean asGuest = Objects.equals(booking.getUserId(), userId);
        if (!asOwner && !asGuest) {
            throw new BusinessException("预约不存在");
        }
        if (status == null) {
            throw new BusinessException("状态取值不合法");
        }
        if (asGuest) {
            if (status != BOOKING_CANCELED) {
                throw new BusinessException("预约人只能取消预约");
            }
            if (booking.getStatus() != BOOKING_PENDING && booking.getStatus() != BOOKING_CONFIRMED) {
                throw new BusinessException("当前状态不可取消");
            }
        } else {
            if (status != BOOKING_CONFIRMED && status != BOOKING_REJECTED) {
                throw new BusinessException("房东只能确认或拒绝预约");
            }
            if (booking.getStatus() != BOOKING_PENDING) {
                throw new BusinessException("该预约已处理");
            }
        }
        booking.setStatus(status);
        booking.setUpdateTime(LocalDateTime.now());
        bookingMapper.updateById(booking);

        // 站内信通知对方
        Long notifyUserId = asOwner ? booking.getUserId() : booking.getOwnerId();
        String action = status == BOOKING_CONFIRMED ? "已确认" : status == BOOKING_REJECTED ? "已拒绝" : "已取消";
        messageService.send(notifyUserId, NotificationEventEnum.POST_COMMENTED, "PROPERTY_BOOKING", booking
            .getId(), "看房预约" + action, "你的看房预约" + action + ",点击查看详情");
    }

    private PropertyDO requireProperty(Long propertyId) {
        PropertyDO property = propertyMapper.selectById(propertyId);
        if (property == null) {
            throw new BusinessException("房源不存在");
        }
        return property;
    }

    /** 按互动表 id 顺序装配房源(已删除/下架的房源自动跳过) */
    private PageResp<PropertyResp> toPropertyPage(List<Long> propertyIds, long total) {
        Map<Long, PropertyDO> properties = propertyIds.isEmpty()
            ? Map.of()
            : propertyMapper.selectByIds(propertyIds)
                .stream()
                .collect(Collectors.toMap(PropertyDO::getId, Function.identity()));
        List<PropertyResp> list = propertyIds.stream()
            .map(properties::get)
            .filter(Objects::nonNull)
            .map(PropertyRespAssembler::toResp)
            .toList();
        PageResp<PropertyResp> pageResp = new PageResp<>();
        pageResp.setList(list);
        pageResp.setTotal(total);
        return pageResp;
    }

    private PageResp<PropertyBookingResp> toBookingPage(List<PropertyBookingDO> records, long total) {
        List<Long> propertyIds = records.stream().map(PropertyBookingDO::getPropertyId).distinct().toList();
        List<Long> userIds = records.stream().map(PropertyBookingDO::getUserId).distinct().toList();
        Map<Long, PropertyDO> properties = propertyIds.isEmpty()
            ? Map.of()
            : propertyMapper.selectByIds(propertyIds)
                .stream()
                .collect(Collectors.toMap(PropertyDO::getId, Function.identity()));
        Map<Long, AppUserDO> users = userIds.isEmpty()
            ? Map.of()
            : appUserMapper.selectByIds(userIds)
                .stream()
                .collect(Collectors.toMap(AppUserDO::getId, Function.identity()));
        List<PropertyBookingResp> list = records.stream().map(booking -> {
            PropertyBookingResp resp = new PropertyBookingResp();
            resp.setId(booking.getId());
            resp.setPropertyId(booking.getPropertyId());
            resp.setUserId(booking.getUserId());
            resp.setOwnerId(booking.getOwnerId());
            resp.setBookingTime(booking.getBookingTime());
            resp.setContactName(booking.getContactName());
            resp.setContactPhone(booking.getContactPhone());
            resp.setRemark(booking.getRemark());
            resp.setStatus(booking.getStatus());
            resp.setCreateTime(booking.getCreateTime());
            PropertyDO property = properties.get(booking.getPropertyId());
            if (property != null) {
                resp.setPropertyTitle(property.getTitle());
                resp.setPropertyPrice(property.getPrice());
                resp.setPropertyPriceType(property.getPriceType());
                if (StrUtil.isNotBlank(property.getImages())) {
                    List<String> images = JSONUtil.toList(property.getImages(), String.class);
                    if (!images.isEmpty()) {
                        resp.setPropertyImage(images.get(0));
                    }
                }
            }
            AppUserDO user = users.get(booking.getUserId());
            if (user != null) {
                resp.setUserNickname(user.getNickname());
                resp.setUserAvatar(user.getAvatar());
            }
            return resp;
        }).toList();
        PageResp<PropertyBookingResp> pageResp = new PageResp<>();
        pageResp.setList(list);
        pageResp.setTotal(total);
        return pageResp;
    }

    private int pageNum(PageQuery pageQuery) {
        return pageQuery == null || pageQuery.getPage() == null ? 1 : pageQuery.getPage();
    }

    private int pageSize(PageQuery pageQuery) {
        return pageQuery == null || pageQuery.getSize() == null ? 10 : pageQuery.getSize();
    }
}
