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

package com.guardfind.server.post.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 发布动态请求
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@Schema(description = "发布动态请求")
public class PostCreateReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "正文", example = "周末带娃去了植物园,春光正好")
    @NotBlank(message = "正文不能为空")
    @Length(max = 1000, message = "正文不能超过 {max} 个字符")
    private String content;

    @Schema(description = "图片 URL 列表(最多 9 张,先经 /file/upload 上传)")
    @Size(max = 9, message = "图片最多 9 张")
    private List<String> images;

    @Schema(description = "位置名称(用户手输/手选,可空)", example = "植物园东门")
    @Length(max = 50, message = "位置名称不能超过 {max} 个字符")
    private String locationName;

    @Schema(description = "经度(可选)")
    private BigDecimal longitude;

    @Schema(description = "纬度(可选)")
    private BigDecimal latitude;
}
