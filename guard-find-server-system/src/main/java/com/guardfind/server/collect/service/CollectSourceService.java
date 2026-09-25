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

package com.guardfind.server.collect.service;

import com.guardfind.server.collect.model.query.CollectSourceQuery;
import com.guardfind.server.collect.model.req.CollectSourceReq;
import com.guardfind.server.collect.model.resp.CollectSourceResp;
import com.guardfind.server.common.base.service.BaseService;

import java.util.List;

/**
 * 采集源配置业务接口
 *
 * <p>
 * 除标准 CRUD 外，仅提供"调度可见源清单"这一个读口径：调度只遍历 {@code enabled=true} 的源，
 * 新增源默认关闭，杜绝"配了就被采"。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public interface CollectSourceService extends BaseService<CollectSourceResp, CollectSourceResp, CollectSourceQuery, CollectSourceReq> {

    /**
     * 已启用的采集源（调度入口）
     *
     * @return 源配置列表
     */
    List<CollectSourceResp> listEnabled();
}
