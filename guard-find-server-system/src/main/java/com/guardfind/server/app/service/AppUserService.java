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

package com.guardfind.server.app.service;

import com.guardfind.server.app.context.AppUserContext;
import com.guardfind.server.app.model.AppLoginUser;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.app.model.req.AppDeactivateReq;
import com.guardfind.server.app.model.req.AppLoginReq;
import com.guardfind.server.app.model.req.AppProfileReq;
import com.guardfind.server.app.model.resp.AppDeactivateResp;
import com.guardfind.server.app.model.resp.AppUserResp;

/**
 * C 端用户业务接口
 *
 * @author guardfind
 * @since 2026/9/24
 */
public interface AppUserService {

    /**
     * 根据 ID 查询 C 端用户（不存在则抛出业务异常）
     *
     * @param id 用户 ID
     * @return 用户实体
     */
    AppUserDO getById(Long id);

    /**
     * 根据手机号查询 C 端用户
     *
     * @param phone 手机号（明文）
     * @return 用户实体，不存在时为 null
     */
    AppUserDO findByPhone(String phone);

    /**
     * 根据手机号获取用户，未注册时自动建档（幂等）
     *
     * @param phone 手机号
     * @return 登录主体（含是否新建标记）
     */
    AppLoginUser obtainOrRegisterByPhone(String phone);

    /**
     * 查询 C 端用户信息（手机号脱敏、含未读消息数）
     *
     * @param id 用户 ID
     * @return 用户信息
     */
    AppUserResp getResp(Long id);

    /**
     * 更新资料
     *
     * @param id  用户 ID
     * @param req 请求参数
     * @return 更新后的用户信息
     */
    AppUserResp updateProfile(Long id, AppProfileReq req);

    /**
     * 设置/修改密码（以 BCRYPT 存储）
     *
     * @param id       用户 ID
     * @param password 新密码（明文，落库时由字段加密拦截器按 BCRYPT 编码）
     */
    void updatePassword(Long id, String password);

    /**
     * 绑定手机号
     *
     * @param id    用户 ID
     * @param phone 手机号
     */
    void bindPhone(Long id, String phone);

    /**
     * 申请注销（进入冷静期，期内登录可撤销）
     *
     * @param id  用户 ID
     * @param req 请求参数（注销原因随请求日志登记到 sys_log）
     * @return 冷静期信息
     */
    AppDeactivateResp deactivate(Long id, AppDeactivateReq req);

    /**
     * 撤销处于冷静期的注销申请（登录时调用）
     *
     * @param user 用户实体
     * @return true：本次登录撤销了注销申请
     */
    boolean cancelDeactivationIfCancelling(AppUserDO user);

    /**
     * 登录成功后置处理（更新活跃时间、登记设备）
     *
     * @param user     用户实体
     * @param loginReq 登录请求参数（携带设备信息，可为 null）
     */
    void afterLogin(AppUserDO user, AppLoginReq loginReq);

    /**
     * 构建 C 端登录域上下文
     *
     * @param user 用户实体
     * @return 上下文
     */
    AppUserContext buildContext(AppUserDO user);
}
