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

package com.guardfind.server.tool.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * 执行签到请求
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@Schema(description = "执行签到请求")
public class ToolCheckinReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "发起人屏幕上的 6 位动态码", example = "368779")
    @NotBlank(message = "请输入签到码")
    @Pattern(regexp = "^\\d{6}$", message = "签到码为 6 位数字")
    private String code;

    @Schema(description = "所在地点名(活动设置了地点时必填且须一致)", example = "图书馆 302")
    @Length(max = 50, message = "地点名不能超过 {max} 个字符")
    private String locationName;
}
