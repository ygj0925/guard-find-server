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

package com.guardfind.server.lead.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 线索提交请求参数
 *
 * <p>
 * 描述长度与图片数在请求与业务两处校验：请求注解保证快速失败与统一提示文案，业务校验保证绕过 HTTP 层的调用
 * （如后台补录）同样受限。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "线索提交请求参数")
public class LeadReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 发现时间
     */
    @Schema(description = "发现时间", example = "2026-09-20 18:30:00", type = "string")
    private LocalDateTime occurTime;

    /**
     * 发现地点描述
     */
    @Schema(description = "发现地点描述", example = "曲周县凤凰新区")
    @Length(max = 255, message = "发现地点长度不能超过 {max} 个字符")
    private String occurAddress;

    /**
     * 线索描述（10–1000 字）
     */
    @Schema(description = "线索描述（10–1000 字）", example = "在超市见到一名与照片相似的男孩，身边有一名中年女子")
    @Length(min = 10, max = 1000, message = "线索描述长度必须在 {min}-{max} 个字符之间")
    private String description;

    /**
     * 图片地址（最多 3 张）
     */
    @Schema(description = "图片地址（最多 3 张）")
    @Size(max = 3, message = "线索图片最多上传 {max} 张")
    private List<@Length(max = 500, message = "图片地址长度不能超过 {max} 个字符") String> mediaUrls;

    /**
     * 联络方式（IN_APP/PHONE，默认 IN_APP）
     */
    @Schema(description = "联络方式（IN_APP/PHONE）", example = "IN_APP")
    @Pattern(regexp = "IN_APP|PHONE", message = "联络方式仅支持 IN_APP/PHONE")
    private String contactMode;

    /**
     * 联系方式（contactMode=PHONE 时必填，仅档案所有人可见明文）
     */
    @Schema(description = "联系方式（联络方式为 PHONE 时必填）", example = "13800001234")
    @Length(max = 255, message = "联系方式长度不能超过 {max} 个字符")
    private String contactValue;
}
