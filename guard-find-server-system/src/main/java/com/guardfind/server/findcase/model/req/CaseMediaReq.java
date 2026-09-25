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

/**
 * 寻人档案影像资料请求参数（图片须先经 /file/upload 落地到自有存储，单张上限 5MB 由上传侧校验）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "寻人档案影像资料请求参数")
public class CaseMediaReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 地址
     */
    @Schema(description = "地址", example = "https://cdn.tianxiawuguai.com/case/2026/09/24/a.jpg")
    @NotBlank(message = "影像地址不能为空")
    @Length(max = 500, message = "影像地址长度不能超过 {max} 个字符")
    private String url;

    /**
     * 类型（PHOTO/VIDEO）
     */
    @Schema(description = "类型（PHOTO/VIDEO）", example = "PHOTO")
    @Pattern(regexp = "PHOTO|VIDEO", message = "影像类型仅支持 PHOTO/VIDEO")
    private String type;

    /**
     * 排序
     */
    @Schema(description = "排序", example = "0")
    private Integer sort;
}
