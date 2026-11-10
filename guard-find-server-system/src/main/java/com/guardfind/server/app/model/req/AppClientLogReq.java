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
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * App 客户端日志上报(契约 8 节)
 *
 * @author guardfind
 * @since 2026/11/10
 */
@Data
@Schema(description = "App 客户端日志上报参数")
public class AppClientLogReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "级别", example = "ERROR")
    @NotBlank(message = "级别不能为空")
    @Pattern(regexp = "DEBUG|INFO|WARN|ERROR", message = "级别不合法")
    private String level;

    @Schema(description = "消息")
    @NotBlank(message = "消息不能为空")
    @Size(max = 2000, message = "消息长度不能超过 {max} 个字符")
    private String message;

    @Schema(description = "堆栈")
    @Size(max = 8000, message = "堆栈长度不能超过 {max} 个字符")
    private String stack;

    @Schema(description = "平台", example = "android")
    @Size(max = 20, message = "平台长度不能超过 {max} 个字符")
    private String platform;

    @Schema(description = "App 版本", example = "1.0.0")
    @Size(max = 50, message = "版本长度不能超过 {max} 个字符")
    private String appVersion;

    @Schema(description = "当前路由", example = "/publish/case")
    @Size(max = 200, message = "路由长度不能超过 {max} 个字符")
    private String route;

    @Schema(description = "附加信息")
    @Size(max = 30, message = "附加信息不能超过 {max} 项")
    private Map<String, Object> extra;
}
