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

package com.guardfind.server.app.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.guardfind.server.common.base.model.entity.BaseDO;
import com.guardfind.server.common.enums.GenderEnum;
import lombok.Data;
import top.continew.starter.encrypt.field.annotation.FieldEncrypt;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * App 用户实体（C 端身份，独立于后台 sys_user）
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@TableName("gf_app_user")
public class AppUserDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像地址
     */
    private String avatar;

    /**
     * 性别
     */
    private GenderEnum gender;

    /**
     * 手机号码（加密存储）
     */
    @FieldEncrypt
    @TableField(insertStrategy = FieldStrategy.NOT_EMPTY, updateStrategy = FieldStrategy.NOT_EMPTY)
    private String phone;

    /**
     * 密码（BCRYPT 加密存储）
     */
    @FieldEncrypt(encryptor = top.continew.starter.encrypt.password.encoder.encryptor.PasswordEncoderEncryptor.class)
    @TableField(insertStrategy = FieldStrategy.NOT_EMPTY, updateStrategy = FieldStrategy.NOT_EMPTY)
    private String password;

    /**
     * 账号状态（1：正常；2：锁定；3：注销申请中；4：已注销）
     */
    private Integer accountStatus;

    /**
     * 身份类型（GUARDIAN/VOLUNTEER/PUBLIC）
     */
    private String identityType;

    /**
     * 实名状态（UNCHECKED/CHECKING/PASSED/REJECTED）
     */
    private String realNameStatus;

    /**
     * 常驻省编码
     */
    private String provinceCode;

    /**
     * 常驻市编码
     */
    private String cityCode;

    /**
     * 常驻区县编码
     */
    private String districtCode;

    /**
     * 志愿者是否已认证
     */
    private Boolean volunteerVerified;

    /**
     * 注册来源（APP/COLLECT_IMPORT）
     */
    private String registrationSource;

    /**
     * 最后活跃时间
     */
    private LocalDateTime lastActiveTime;

    /**
     * 注销申请时间（冷静期起点）
     */
    private LocalDateTime deactivateApplyTime;
}
