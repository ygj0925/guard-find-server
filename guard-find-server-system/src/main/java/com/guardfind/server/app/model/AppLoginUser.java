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

package com.guardfind.server.app.model;

import com.guardfind.server.app.model.entity.AppUserDO;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * C 端登录主体（登录时解析到的用户及是否为本次登录新建）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Getter
@RequiredArgsConstructor
public class AppLoginUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户实体
     */
    private final AppUserDO user;

    /**
     * 是否本次登录新建（首次验证码登录自动建档）
     */
    private final boolean newUser;

    /**
     * 用户 ID
     *
     * @return 用户 ID
     */
    public Long getUserId() {
        return user == null ? null : user.getId();
    }
}
