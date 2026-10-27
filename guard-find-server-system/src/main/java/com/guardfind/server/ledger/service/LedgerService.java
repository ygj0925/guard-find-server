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

package com.guardfind.server.ledger.service;

import com.guardfind.server.ledger.model.req.LedgerRecordReq;
import com.guardfind.server.ledger.model.resp.LedgerPageResp;
import com.guardfind.server.ledger.model.resp.LedgerSummaryResp;
import top.continew.starter.extension.crud.model.query.PageQuery;

/**
 * 记账业务接口（C 端）
 *
 * @author guardfind
 * @since 2026/10/28
 */
public interface LedgerService {

    /**
     * 新增记账记录
     *
     * @param req 记录内容
     * @return 记录 ID
     */
    Long create(LedgerRecordReq req);

    /**
     * 修改记账记录（校验归属）
     */
    void update(Long id, LedgerRecordReq req);

    /**
     * 删除记账记录（校验归属）
     */
    void delete(Long id);

    /**
     * 分页查询当前用户的记账记录
     *
     * @param month     月份（yyyy-MM，可空，空则查全部）
     * @param pageQuery 分页参数
     */
    LedgerPageResp page(String month, PageQuery pageQuery);

    /**
     * 周报/月报/年报汇总
     *
     * @param period week | month | year
     * @param date   区间内锚点日（yyyy-MM-dd，可空，默认今天）
     */
    LedgerSummaryResp summary(String period, String date);
}
