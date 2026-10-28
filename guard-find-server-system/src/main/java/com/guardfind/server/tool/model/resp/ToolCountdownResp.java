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

package com.guardfind.server.tool.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 倒数日响应(daysLeft 由服务端按服务器时区计算,客户端不重复算)
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Data
@Schema(description = "倒数日响应")
public class ToolCountdownResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "记录 ID")
    private Long id;

    @Schema(description = "事项名称")
    private String title;

    @Schema(description = "目标日期")
    private LocalDate targetDate;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "剩余天数(负数=已过 N 天,0=今天)")
    private Long daysLeft;
}
