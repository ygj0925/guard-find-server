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

package com.guardfind.server.property.service;

import com.guardfind.server.property.model.req.PropertyReq;
import com.guardfind.server.property.model.resp.PropertyResp;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;

/**
 * 房产房源业务接口(C 端)。
 * 权限:浏览已上架=所有登录用户;管理(增删改/上下架)=仅房东本人。
 */
public interface PropertyService {

    Long create(Long userId, PropertyReq req);

    PageResp<PropertyResp> page(String city, String propertyType, String priceType, PageQuery pageQuery);

    PageResp<PropertyResp> mine(Long userId, PageQuery pageQuery);

    PropertyResp detail(Long id);

    void update(Long id, Long userId, PropertyReq req);

    void delete(Long id, Long userId);

    void updateStatus(Long id, Long userId, Integer status);
}
