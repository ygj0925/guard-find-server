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

package com.guardfind.server.tool.service;

import com.guardfind.server.tool.model.req.ToolHabitReq;
import com.guardfind.server.tool.model.resp.ToolHabitResp;

import java.util.List;

/**
 * 习惯打卡业务接口(C 端)
 *
 * @author guardfind
 * @since 2026/10/28
 */
public interface ToolHabitService {

    /**
     * 新建习惯
     */
    Long create(ToolHabitReq req);

    /**
     * 修改习惯(校验归属)
     */
    void update(Long id, ToolHabitReq req);

    /**
     * 删除习惯(级联删打卡流水)
     */
    void delete(Long id);

    /**
     * 习惯列表(带 streak 统计)
     */
    List<ToolHabitResp> list();

    /**
     * 打卡/取消今天(幂等翻转)
     *
     * @return true=已打卡,false=已取消
     */
    boolean toggleToday(Long id);

    /**
     * 某习惯在指定月份的打卡日期(月历网格用)
     *
     * @return yyyy-MM-dd 列表,升序
     */
    List<String> monthLog(Long id, String month);
}
