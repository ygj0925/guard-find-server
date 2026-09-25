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

package com.guardfind.server.app.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;
import com.guardfind.server.app.enums.IdentityTypeEnum;

import java.io.Serial;
import java.io.Serializable;

/**
 * C 端资料完善请求参数（全部字段可选，仅更新提交的字段）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "C 端资料完善请求参数")
public class AppProfileReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 昵称
     */
    @Schema(description = "昵称", example = "无脚星友4821")
    @Length(max = 30, message = "昵称长度不能超过 30 个字符")
    private String nickname;

    /**
     * 头像地址
     */
    @Schema(description = "头像地址", example = "https://guardfind.cn/file/avatar/2026/09/1/xxx.png")
    @Length(max = 500, message = "头像地址长度不能超过 500 个字符")
    private String avatar;

    /**
     * 性别（MALE：男；FEMALE：女；UNKNOWN：未知）
     */
    @Schema(description = "性别（MALE：男；FEMALE：女；UNKNOWN：未知）", example = "MALE")
    @Pattern(regexp = "^(MALE|FEMALE|UNKNOWN)$", message = "性别取值无效")
    private String gender;

    /**
     * 身份类型（GUARDIAN：家属；VOLUNTEER：志愿者；PUBLIC：普通用户）
     */
    @Schema(description = "身份类型（GUARDIAN：家属；VOLUNTEER：志愿者；PUBLIC：普通用户）", example = "GUARDIAN")
    private IdentityTypeEnum identityType;

    /**
     * 常驻省编码
     */
    @Schema(description = "常驻省编码", example = "130000")
    @Length(max = 12, message = "省编码长度不能超过 12 个字符")
    private String provinceCode;

    /**
     * 常驻市编码
     */
    @Schema(description = "常驻市编码", example = "130400")
    @Length(max = 12, message = "市编码长度不能超过 12 个字符")
    private String cityCode;

    /**
     * 常驻区县编码
     */
    @Schema(description = "常驻区县编码", example = "130431")
    @Length(max = 12, message = "区县编码长度不能超过 12 个字符")
    private String districtCode;
}
