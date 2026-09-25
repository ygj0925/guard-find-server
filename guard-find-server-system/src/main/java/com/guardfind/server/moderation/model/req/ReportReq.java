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

package com.guardfind.server.moderation.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 举报提交请求参数
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "举报提交请求参数")
public class ReportReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 目标类型（CASE/LEAD/USER）
     */
    @Schema(description = "目标类型（CASE/LEAD/USER）", example = "CASE")
    @NotBlank(message = "举报目标类型不能为空")
    private String targetType;

    /**
     * 目标 ID
     */
    @Schema(description = "目标 ID", example = "1790000000000000001")
    @NotNull(message = "举报目标不能为空")
    private Long targetId;

    /**
     * 举报理由（字典 gf_report_reason）
     */
    @Schema(description = "举报理由（字典 gf_report_reason）", example = "FRAUD")
    @NotBlank(message = "举报理由不能为空")
    @Length(max = 24, message = "举报理由长度不能超过 {max} 个字符")
    private String reasonType;

    /**
     * 补充描述
     */
    @Schema(description = "补充描述", example = "发布人在评论区要求先转 200 元核实费")
    @Length(max = 500, message = "补充描述长度不能超过 {max} 个字符")
    private String description;

    /**
     * 证据图片（最多 3 张）
     */
    @Schema(description = "证据图片（最多 3 张）")
    @Size(max = 3, message = "证据图片最多上传 {max} 张")
    private List<String> mediaUrls;
}
