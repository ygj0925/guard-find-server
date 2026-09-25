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
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * 举报处置请求参数
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "举报处置请求参数")
public class ReportHandleReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 处置动作（APPROVE_REPORT/DISMISS）
     */
    @Schema(description = "处置动作（APPROVE_REPORT/DISMISS）", example = "APPROVE_REPORT")
    @NotBlank(message = "处置动作不能为空")
    private String action;

    /**
     * 处置说明（同步给举报人与被举报人）
     */
    @Schema(description = "处置说明", example = "举报成立，已下架该档案并对发布人禁言 7 天")
    @Length(max = 500, message = "处置说明长度不能超过 {max} 个字符")
    private String remark;

    /**
     * 对被举报用户的处置（可选）
     */
    @Schema(description = "对被举报用户的处置（可选）")
    @Valid
    private PunishmentReq punishment;

    /**
     * 用户处置参数
     */
    @Data
    @Schema(description = "用户处置参数")
    public static class PunishmentReq implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 处置类型（MUTE/BAN）
         */
        @Schema(description = "处置类型（MUTE/BAN）", example = "MUTE")
        @NotBlank(message = "处置类型不能为空")
        private String type;

        /**
         * 时长（天，空为该类型默认时长；传 -1 表示永久）
         */
        @Schema(description = "时长（天，空为默认时长；-1 表示永久）", example = "7")
        @Min(value = -1, message = "处置时长仅支持 -1（永久）或正整数天")
        @Max(value = 3650, message = "处置时长不能超过 {max} 天")
        private Integer days;
    }
}
