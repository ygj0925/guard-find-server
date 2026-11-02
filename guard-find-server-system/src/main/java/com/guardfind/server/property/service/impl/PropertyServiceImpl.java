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
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.property.mapper.PropertyMapper;
import com.guardfind.server.property.model.entity.PropertyDO;
import com.guardfind.server.property.model.req.PropertyReq;
import com.guardfind.server.property.model.resp.PropertyManagerResp;
import com.guardfind.server.property.model.resp.PropertyResp;
import com.guardfind.server.property.service.PropertyInteractionService;
import com.guardfind.server.property.service.PropertyService;
import com.guardfind.server.property.support.PropertyRespAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 房产房源实现。权限:浏览已上架=所有用户;增删改/上下架=仅房东本人。
 */
@Service
@RequiredArgsConstructor
public class PropertyServiceImpl implements PropertyService {

    private final PropertyMapper propertyMapper;
    private final AppUserMapper appUserMapper;
    private final PropertyInteractionService interactionService;

    @Override
    public Long create(Long userId, PropertyReq req) {
        PropertyDO property = new PropertyDO();
        applyReq(property, req);
        property.setUserId(userId);
        property.setStatus(req.getStatus() != null ? req.getStatus() : 1);
        property.setViewCount(0);
        propertyMapper.insert(property);
        return property.getId();
    }

    @Override
    public PageResp<PropertyResp> page(String city,
                                       String propertyType,
                                       String priceType,
                                       String rentalMode,
                                       PageQuery pageQuery) {
        Page<PropertyDO> page = propertyMapper.selectPage(new Page<>(pageNum(pageQuery), pageSize(pageQuery)), Wrappers
            .<PropertyDO>lambdaQuery()
            .eq(PropertyDO::getStatus, 2)
            .eq(StrUtil.isNotBlank(city), PropertyDO::getCity, city)
            .eq(StrUtil.isNotBlank(propertyType), PropertyDO::getPropertyType, propertyType)
            .eq(StrUtil.isNotBlank(priceType), PropertyDO::getPriceType, priceType)
            .orderByDesc(PropertyDO::getCreateTime));
        return toPage(page);
    }

    @Override
    public PageResp<PropertyResp> mine(Long userId, PageQuery pageQuery) {
        Page<PropertyDO> page = propertyMapper.selectPage(new Page<>(pageNum(pageQuery), pageSize(pageQuery)), Wrappers
            .<PropertyDO>lambdaQuery()
            .eq(PropertyDO::getUserId, userId)
            .orderByDesc(PropertyDO::getCreateTime));
        return toPage(page);
    }

    @Override
    public PropertyResp detail(Long id) {
        PropertyDO property = propertyMapper.selectById(id);
        if (property == null) {
            throw new BusinessException("房源不存在");
        }
        // 浏览计数自增
        propertyMapper.update(null, Wrappers.<PropertyDO>update().eq("id", id).setSql("view_count = view_count + 1"));
        property.setViewCount(property.getViewCount() + 1);
        PropertyResp resp = toResp(property);
        AppUserDO owner = appUserMapper.selectById(property.getUserId());
        if (owner != null) {
            fillOwner(resp, owner);
        }
        // 登录用户:记录浏览历史 + 回填收藏状态(匿名浏览只计数)
        Long viewerId = AppUserContextHolder.getUserId();
        if (viewerId != null) {
            interactionService.recordView(viewerId, id);
            resp.setIsFavorited(interactionService.isFavorited(viewerId, id));
        }
        return resp;
    }

    @Override
    public void update(Long id, Long userId, PropertyReq req) {
        PropertyDO property = getOwned(id, userId);
        applyReq(property, req);
        propertyMapper.updateById(property);
    }

    @Override
    public void delete(Long id, Long userId) {
        PropertyDO property = getOwned(id, userId);
        propertyMapper.deleteById(property.getId());
    }

    @Override
    public void updateStatus(Long id, Long userId, Integer status) {
        PropertyDO property = getOwned(id, userId);
        if (status == null || status < 1 || status > 3) {
            throw new BusinessException("状态取值不合法");
        }
        property.setStatus(status);
        propertyMapper.updateById(property);
    }

    @Override
    public PageResp<PropertyResp> pageForAdmin(Integer status, String city, String priceType, PageQuery pageQuery) {
        Page<PropertyDO> page = propertyMapper.selectPage(new Page<>(pageNum(pageQuery), pageSize(pageQuery)), Wrappers
            .<PropertyDO>lambdaQuery()
            .eq(status != null && status > 0, PropertyDO::getStatus, status)
            .eq(StrUtil.isNotBlank(city), PropertyDO::getCity, city)
            .eq(StrUtil.isNotBlank(priceType), PropertyDO::getPriceType, priceType)
            .orderByDesc(PropertyDO::getCreateTime));
        return toPage(page);
    }

    @Override
    public void updateStatusForAdmin(Long id, Integer status) {
        if (status == null || status < 1 || status > 3) {
            throw new BusinessException("状态取值不合法");
        }
        PropertyDO property = propertyMapper.selectById(id);
        if (property == null) {
            throw new BusinessException("房源不存在");
        }
        property.setStatus(status);
        propertyMapper.updateById(property);
    }

    @Override
    public void deleteForAdmin(Long id) {
        if (propertyMapper.selectById(id) == null) {
            throw new BusinessException("房源不存在");
        }
        propertyMapper.deleteById(id);
    }

    @Override
    public void setManager(Long userId, boolean isManager) {
        AppUserDO user = appUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setIsPropertyManager(isManager ? 1 : 0);
        appUserMapper.updateById(user);
    }

    @Override
    public PageResp<PropertyManagerResp> listManagers(PageQuery pageQuery) {
        Page<AppUserDO> page = appUserMapper.selectPage(new Page<>(pageNum(pageQuery), pageSize(pageQuery)), Wrappers
            .<AppUserDO>lambdaQuery()
            .eq(AppUserDO::getIsPropertyManager, 1)
            .orderByDesc(AppUserDO::getId));
        List<PropertyManagerResp> list = page.getRecords().stream().map(user -> {
            PropertyManagerResp resp = new PropertyManagerResp();
            resp.setUserId(user.getId());
            resp.setNickname(user.getNickname());
            resp.setAvatar(user.getAvatar());
            resp.setPhone(user.getPhone());
            return resp;
        }).toList();
        PageResp<PropertyManagerResp> pageResp = new PageResp<>();
        pageResp.setList(list);
        pageResp.setTotal(page.getTotal());
        return pageResp;
    }

    private void applyReq(PropertyDO property, PropertyReq req) {
        property.setTitle(StrUtil.trim(req.getTitle()));
        property.setDescription(StrUtil.trimToNull(req.getDescription()));
        property.setPropertyType(req.getPropertyType());
        property.setLayout(StrUtil.trimToNull(req.getLayout()));
        property.setAreaSqm(req.getAreaSqm());
        property.setFloorInfo(StrUtil.trimToNull(req.getFloorInfo()));
        property.setOrientation(StrUtil.trimToNull(req.getOrientation()));
        property.setDecoration(StrUtil.trimToNull(req.getDecoration()));
        property.setPrice(req.getPrice());
        property.setPriceType(req.getPriceType());
        property.setCommunity(StrUtil.trimToNull(req.getCommunity()));
        property.setAvailableFrom(req.getAvailableFrom());
        property.setRentalMode(StrUtil.trimToNull(req.getRentalMode()));
        property.setAddress(StrUtil.trim(req.getAddress()));
        property.setDistrict(StrUtil.trimToNull(req.getDistrict()));
        property.setCity(StrUtil.trimToNull(req.getCity()));
        property.setFacilities(req.getFacilities() != null && !req.getFacilities().isEmpty()
            ? JSONUtil.toJsonStr(req.getFacilities())
            : null);
        property.setContactName(StrUtil.trimToNull(req.getContactName()));
        property.setContactPhone(StrUtil.trimToNull(req.getContactPhone()));
        if (req.getImages() != null && !req.getImages().isEmpty()) {
            if (req.getImages().size() > 9)
                throw new BusinessException("图片最多 9 张");
            property.setImages(JSONUtil.toJsonStr(req.getImages()));
        }
    }

    private PropertyDO getOwned(Long id, Long userId) {
        PropertyDO property = propertyMapper.selectById(id);
        if (property == null || !property.getUserId().equals(userId)) {
            throw new BusinessException("房源不存在");
        }
        return property;
    }

    private PageResp<PropertyResp> toPage(Page<PropertyDO> page) {
        List<Long> userIds = page.getRecords().stream().map(PropertyDO::getUserId).distinct().toList();
        Map<Long, AppUserDO> users = userIds.isEmpty()
            ? Map.of()
            : appUserMapper.selectByIds(userIds)
                .stream()
                .collect(Collectors.toMap(AppUserDO::getId, Function.identity()));
        List<PropertyResp> list = page.getRecords().stream().map(property -> {
            PropertyResp resp = toResp(property);
            AppUserDO user = users.get(property.getUserId());
            if (user != null) {
                fillOwner(resp, user);
            }
            return resp;
        }).toList();
        PageResp<PropertyResp> pageResp = new PageResp<>();
        pageResp.setList(list);
        pageResp.setTotal(page.getTotal());
        return pageResp;
    }

    private void fillOwner(PropertyResp resp, AppUserDO user) {
        resp.setOwnerNickname(user.getNickname());
        resp.setOwnerAvatar(user.getAvatar());
        resp.setOwnerIsManager(user.getIsPropertyManager() != null && user.getIsPropertyManager() == 1);
    }

    private PropertyResp toResp(PropertyDO property) {
        return PropertyRespAssembler.toResp(property);
    }

    private int pageNum(PageQuery pageQuery) {
        return pageQuery == null || pageQuery.getPage() == null ? 1 : pageQuery.getPage();
    }

    private int pageSize(PageQuery pageQuery) {
        return pageQuery == null || pageQuery.getSize() == null ? 10 : pageQuery.getSize();
    }
}
