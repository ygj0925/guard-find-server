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

package com.guardfind.server.app.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * C 端微信小程序一键登录请求参数(wx.login 的 code 换 openid)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "C 端微信小程序一键登录请求参数")
public class AppWechatLoginReq extends AppLoginReq {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * wx.login 获取的临时登录凭证
     */
    @Schema(description = "wx.login 获取的临时登录凭证", example = "0a3XXXXXXXX")
    @NotBlank(message = "微信登录凭证不能为空")
    private String code;
}
