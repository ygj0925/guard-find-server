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

package com.guardfind.server.tool.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建汇报活动请求
 *
 * <p>
 * 填报人/接收人从用户 ID 列表指定(客户端从关注列表选人);
 * 发起人自动成为 OWNER 角色。全部成员可阅读已提交月报并评论。
 * </p>
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@Schema(description = "创建汇报活动请求")
public class ToolReportCreateReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "汇报标题", example = "10 月月度工作汇报")
    @NotBlank(message = "汇报标题不能为空")
    @Length(max = 100, message = "标题不能超过 {max} 个字符")
    private String title;

    @Schema(description = "汇报周期(yyyy-MM)", example = "2026-10")
    @NotBlank(message = "汇报周期不能为空")
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "周期格式应为 yyyy-MM")
    private String period;

    @Schema(description = "提交截止时间(可空,超期提交标记迟交)")
    private LocalDateTime dueDate;

    @Schema(description = "填报人用户 ID 列表(至少 1 人)")
    @NotNull(message = "填报人不能为空")
    @Length(min = 1, max = 30, message = "填报人 1-30 人")
    private List<Long> assigneeUserIds;

    @Schema(description = "接收人用户 ID 列表(只读+评论,可空)")
    @Length(max = 30, message = "接收人最多 {max} 人")
    private List<Long> viewerUserIds;
}
