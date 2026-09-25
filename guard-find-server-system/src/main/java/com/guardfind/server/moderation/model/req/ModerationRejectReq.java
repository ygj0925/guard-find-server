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
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;

/**
 * 审核驳回请求参数
 *
 * <p>
 * {@code reasonCode} 必须取自字典 {@code gf_reject_reason}：审核结论要能被 App 侧翻译成标准文案，
 * 也便于按理由统计驳回原因分布，因此不接受自由文本充当理由。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "审核驳回请求参数")
public class ModerationRejectReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 理由码（字典 gf_reject_reason）
     */
    @Schema(description = "理由码（字典 gf_reject_reason）", example = "INCOMPLETE")
    @NotBlank(message = "驳回理由码不能为空")
    @Length(max = 32, message = "驳回理由码长度不能超过 {max} 个字符")
    private String reasonCode;

    /**
     * 补充说明
     */
    @Schema(description = "补充说明", example = "缺少失踪当日的时间与地点")
    @Length(max = 500, message = "补充说明长度不能超过 {max} 个字符")
    private String remark;
}
