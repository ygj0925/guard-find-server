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

import com.guardfind.server.tool.model.req.ToolTodoReq;
import com.guardfind.server.tool.model.resp.ToolTodoResp;

import java.util.List;

/**
 * 待办业务接口(C 端)
 *
 * @author guardfind
 * @since 2026/10/28
 */
public interface ToolTodoService {

    /**
     * 新建待办
     */
    Long create(ToolTodoReq req);

    /**
     * 修改待办(内容/截止日;校验归属)
     */
    void update(Long id, ToolTodoReq req);

    /**
     * 切换完成状态(校验归属)
     */
    void toggle(Long id);

    /**
     * 删除待办(校验归属)
     */
    void delete(Long id);

    /**
     * 当前用户待办列表:未完成在前(截止日升序,无截止日的按创建时间倒序),
     * 已完成在后(完成时间倒序);daysLeft 类派生展示由客户端算。
     */
    List<ToolTodoResp> list();
}
