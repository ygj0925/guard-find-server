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

package com.guardfind.server.plaza.service;

import com.guardfind.server.findcase.model.query.CaseFeedQuery;
import com.guardfind.server.findcase.model.resp.CasePublicResp;
import com.guardfind.server.plaza.model.req.TrackReq;
import com.guardfind.server.plaza.model.resp.FeedCursorResp;
import com.guardfind.server.plaza.model.resp.PlazaFilterResp;
import com.guardfind.server.plaza.model.resp.PlazaStatisticsResp;
import jakarta.validation.Valid;

/**
 * 广场业务接口
 *
 * @author guardfind
 * @since 2026/9/24
 */
public interface PlazaService {

    /**
     * 广场信息流
     *
     * @param sort         排序方式：latest/urgent/nearby/found
     * @param provinceCode 省编码（nearby 流必填其一）
     * @param cityCode     市编码
     * @param keyword      关键词
     * @param cursor       游标（首屏为空）
     * @param size         每页条数
     * @return 游标分页结果
     */
    FeedCursorResp<CasePublicResp> feed(String sort,
                                        String provinceCode,
                                        String cityCode,
                                        String keyword,
                                        String cursor,
                                        Integer size);

    /**
     * 广场信息流（含搜索扩展条件）
     *
     * @param query 查询条件
     * @return 游标分页结果
     */
    FeedCursorResp<CasePublicResp> feed(CaseFeedQuery query);

    /**
     * 关键词搜索（复用信息流的状态过滤与游标机制，并记录登录用户的搜索历史）
     *
     * @param query 查询条件（keyword 必填）
     * @return 游标分页结果
     */
    FeedCursorResp<CasePublicResp> search(@Valid CaseFeedQuery query);

    /**
     * 筛选器元数据
     *
     * @return 区划树与字典项
     */
    PlazaFilterResp filters();

    /**
     * 广场统计
     *
     * @return 统计结果
     */
    PlazaStatisticsResp statistics();

    /**
     * 埋点上报（允许匿名）
     *
     * @param req 上报参数
     */
    void track(@Valid TrackReq req);

    /**
     * 清空当前登录用户的搜索历史
     */
    void clearSearchHistory();
}
