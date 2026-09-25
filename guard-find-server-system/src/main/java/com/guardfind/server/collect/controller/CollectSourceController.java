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

package com.guardfind.server.collect.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;
import com.guardfind.server.collect.model.query.CollectSourceQuery;
import com.guardfind.server.collect.model.req.CollectSourceReq;
import com.guardfind.server.collect.model.resp.CollectSourceResp;
import com.guardfind.server.collect.service.CollectSourceService;
import com.guardfind.server.common.base.controller.BaseController;
import top.continew.starter.extension.crud.annotation.CrudRequestMapping;
import top.continew.starter.extension.crud.enums.Api;
import top.continew.starter.log.annotation.Log;

/**
 * 采集源配置管理 API
 *
 * <p>
 * 标准 CRUD（分页/详情/新增/修改/批量删除）。权限码由 {@code @CrudRequestMapping} 按路径派生，
 * 见 {@code BaseController#preHandle}；手动触发采集在 {@code CollectController} 的
 * {@code POST /system/collect/run/{sourceCode}}。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Tag(name = "采集源配置管理 API")
@RestController
@Log(module = "寻亲采集")
@CrudRequestMapping(value = "/system/collect/source", api = {Api.PAGE, Api.GET, Api.CREATE, Api.UPDATE,
    Api.BATCH_DELETE})
public class CollectSourceController extends BaseController<CollectSourceService, CollectSourceResp, CollectSourceResp, CollectSourceQuery, CollectSourceReq> {
}
