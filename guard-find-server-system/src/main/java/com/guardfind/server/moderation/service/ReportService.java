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

package com.guardfind.server.moderation.service;

import com.guardfind.server.moderation.model.req.ReportReq;
import com.guardfind.server.moderation.model.resp.ReportPageResp;
import com.guardfind.server.moderation.model.resp.ReportResp;
import top.continew.starter.extension.crud.model.query.PageQuery;

/**
 * 举报业务接口（C 端）
 *
 * @author guardfind
 * @since 2026/9/24
 */
public interface ReportService {

    /**
     * 提交举报（同一举报人对同一目标去重，重复举报累加 {@code hitCount}）
     *
     * @param req 请求参数
     * @return 举报视图
     */
    ReportResp report(ReportReq req);

    /**
     * 我提交的举报
     *
     * @param pageQuery 分页参数
     * @return 分页响应
     */
    ReportPageResp mine(PageQuery pageQuery);
}
