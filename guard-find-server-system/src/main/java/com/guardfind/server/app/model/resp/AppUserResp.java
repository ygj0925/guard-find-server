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

package com.guardfind.server.app.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * C 端用户响应参数
 *
 * <p>
 * 手机号在业务层即完成脱敏（不依赖序列化注解），确保该 DTO 被复用或落日志时不会泄露明文。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "C 端用户响应参数")
public class AppUserResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     */
    @Schema(description = "用户 ID", example = "1789000000000000001")
    private Long id;

    /**
     * 昵称
     */
    @Schema(description = "昵称", example = "无脚星友4821")
    private String nickname;

    /**
     * 头像地址
     */
    @Schema(description = "头像地址", example = "https://guardfind.cn/file/avatar/2026/09/1/xxx.png")
    private String avatar;

    /**
     * 性别（MALE：男；FEMALE：女；UNKNOWN：未知）
     */
    @Schema(description = "性别（MALE：男；FEMALE：女；UNKNOWN：未知）", example = "UNKNOWN")
    private String gender;

    /**
     * 手机号码（已脱敏）
     */
    @Schema(description = "手机号码（已脱敏）", example = "138****1234")
    private String phone;

    /**
     * 是否已绑定手机号
     */
    @Schema(description = "是否已绑定手机号", example = "true")
    private Boolean boundPhone;

    /**
     * 身份类型（GUARDIAN：家属；VOLUNTEER：志愿者；PUBLIC：普通用户）
     */
    @Schema(description = "身份类型（GUARDIAN：家属；VOLUNTEER：志愿者；PUBLIC：普通用户）", example = "PUBLIC")
    private String identityType;

    /**
     * 实名状态（UNCHECKED：未认证；CHECKING：认证中；PASSED：已通过；REJECTED：已驳回）
     */
    @Schema(description = "实名状态（UNCHECKED：未认证；CHECKING：认证中；PASSED：已通过；REJECTED：已驳回）", example = "UNCHECKED")
    private String realNameStatus;

    /**
     * 常驻省编码
     */
    @Schema(description = "常驻省编码", example = "130000")
    private String provinceCode;

    /**
     * 常驻市编码
     */
    @Schema(description = "常驻市编码", example = "130400")
    private String cityCode;

    /**
     * 常驻区县编码
     */
    @Schema(description = "常驻区县编码", example = "130431")
    private String districtCode;

    /**
     * 志愿者是否已认证
     */
    @Schema(description = "志愿者是否已认证", example = "false")
    private Boolean volunteerVerified;

    /**
     * 是否已设置密码
     */
    @Schema(description = "是否已设置密码", example = "false")
    private Boolean hasPassword;

    /**
     * 未读消息数
     */
    @Schema(description = "未读消息数", example = "3")
    private Long unreadCount;

    /**
     * 注册时间
     */
    @Schema(description = "注册时间", example = "2026-09-24 10:00:00", type = "string")
    private LocalDateTime createdAt;
}
