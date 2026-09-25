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

package com.guardfind.server.findcase.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 寻人档案找回确认请求参数（找回必须留依据，避免误标喜报）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "寻人档案找回确认请求参数")
public class CaseFoundReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 确认依据（POLICE：警方确认；GUARDIAN：监护人确认；DNA：DNA 比对）
     */
    @Schema(description = "确认依据（POLICE/GUARDIAN/DNA）", example = "POLICE")
    @NotBlank(message = "找回确认依据不能为空")
    @Pattern(regexp = "POLICE|GUARDIAN|DNA", message = "找回确认依据仅支持 POLICE/GUARDIAN/DNA")
    private String confirmType;

    /**
     * 找回时间
     */
    @Schema(description = "找回时间", example = "2026-09-24 10:00:00", type = "string")
    private LocalDateTime foundTime;

    /**
     * 找回说明
     */
    @Schema(description = "找回说明", example = "经警方 DNA 比对确认，已于今日团聚")
    @Length(max = 500, message = "找回说明长度不能超过 {max} 个字符")
    private String remark;
}
