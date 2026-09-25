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

package com.guardfind.server.plaza.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

import java.io.Serial;
import java.io.Serializable;

/**
 * 广场埋点上报请求参数（允许匿名上报，服务端按 IP 限流）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "广场埋点上报请求参数")
public class TrackReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 事件类型（EXPOSE/CLICK/SHARE）
     */
    @Schema(description = "事件类型（EXPOSE/CLICK/SHARE）", example = "CLICK")
    @NotBlank(message = "事件类型不能为空")
    @Pattern(regexp = "EXPOSE|CLICK|SHARE|PUBLISH", message = "事件类型仅支持 EXPOSE/CLICK/SHARE/PUBLISH")
    private String eventType;

    /**
     * 目标 ID（档案 ID）
     */
    @Schema(description = "目标 ID", example = "1790000000000000000")
    private Long targetId;

    /**
     * 目标所属档案编号或描述（用于与埋点看板对齐，不参与业务判断）
     */
    @Schema(description = "目标所属档案编号或描述", example = "WG260924-2QK3X9ZT")
    @Pattern(regexp = "^.{0,64}$", message = "目标描述长度不能超过 64 个字符")
    private String targetCase;

    /**
     * 停留时长（毫秒）
     */
    @Schema(description = "停留时长（毫秒）", example = "1200")
    @Range(min = 0, max = 3600000, message = "停留时长必须在 {min} 至 {max} 毫秒之间")
    private Long durationMs;
}
