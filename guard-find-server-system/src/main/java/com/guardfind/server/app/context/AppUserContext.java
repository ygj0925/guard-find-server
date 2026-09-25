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

package com.guardfind.server.app.context;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * App 用户上下文（缓存于 App 登录域的 token session 中）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
public class AppUserContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * App 用户 ID
     */
    private Long id;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 身份类型（GUARDIAN/VOLUNTEER/PUBLIC）
     */
    private String identityType;

    /**
     * 常驻市编码
     */
    private String cityCode;

    /**
     * 客户端 ID
     */
    private String clientId;

    /**
     * 客户端类型
     */
    private String clientType;
}
