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

package com.guardfind.server.app.mapper;

import com.guardfind.server.app.model.entity.AppUserDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.continew.starter.data.mapper.BaseMapper;
import top.continew.starter.encrypt.field.annotation.FieldEncrypt;

/**
 * App 用户 Mapper
 *
 * @author guardfind
 * @since 2026/9/24
 */
public interface AppUserMapper extends BaseMapper<AppUserDO> {

    /**
     * 根据手机号查询（手机号为加密列，参数需经 @FieldEncrypt 加密后再比对）
     *
     * @param phone 手机号明文
     * @return App 用户
     */
    @Select("SELECT * FROM gf_app_user WHERE phone = #{phone} AND deleted = 0 LIMIT 1")
    AppUserDO selectByPhone(@FieldEncrypt @Param("phone") String phone);
}
