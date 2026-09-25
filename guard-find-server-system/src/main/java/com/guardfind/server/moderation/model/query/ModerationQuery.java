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

package com.guardfind.server.moderation.model.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * 待审核队列查询条件
 *
 * <p>
 * 队列按提交时间升序（先进先出），不提供按审核人偏好排序的口子，避免"好审的先审、难审的沉底"。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "待审核队列查询条件")
public class ModerationQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 业务类型（CASE/LEAD/COLLECT_CANDIDATE，为空表示全部可审类型）
     */
    @Schema(description = "业务类型（CASE/LEAD）", example = "CASE")
    private String bizType;

    /**
     * 省编码
     */
    @Schema(description = "省编码", example = "130000")
    @Length(max = 16, message = "区划编码长度不能超过 {max} 个字符")
    private String provinceCode;

    /**
     * 市编码
     */
    @Schema(description = "市编码", example = "130400")
    @Length(max = 16, message = "区划编码长度不能超过 {max} 个字符")
    private String cityCode;
}
