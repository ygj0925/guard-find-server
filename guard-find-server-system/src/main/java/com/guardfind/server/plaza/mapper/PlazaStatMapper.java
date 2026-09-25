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

package com.guardfind.server.plaza.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 广场统计只读 Mapper
 *
 * <p>
 * 仅做跨表计数（线索表归线索域所有，此处只读取聚合值，不引入其实体与写路径），避免广场为了一个数字去依赖线索服务。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Mapper
public interface PlazaStatMapper {

    /**
     * 统计指定时间之后新增的线索数
     *
     * @param since 起始时间（含）
     * @return 线索数
     */
    @Select("SELECT COUNT(1) FROM gf_case_lead WHERE deleted = 0 AND create_time >= #{since}")
    long countLeadsSince(@Param("since") LocalDateTime since);
}
