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

package com.guardfind.server.collect.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.guardfind.server.common.base.model.entity.BaseDO;
import lombok.Data;

import java.io.Serial;
import java.util.List;

/**
 * 采集数据删除审计实体
 *
 * <p>
 * 站方或权利人要求撤回来源数据时的执行凭据：谁请求、范围（来源编号/链接）、影响到哪些候选与档案、结果如何。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@TableName(value = "gf_collect_erase_log", autoResultMap = true)
public class CollectEraseLogDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 采集源标识
     */
    private String sourceCode;

    /**
     * 来源编号列表（JSON 数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> sourceIds;

    /**
     * 来源链接列表（JSON 数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> sourceUrls;

    /**
     * 请求方
     */
    private String requester;

    /**
     * 说明
     */
    private String remark;

    /**
     * 受影响候选 ID（JSON 数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> candidateIds;

    /**
     * 受影响档案 ID（JSON 数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> caseIds;

    /**
     * 执行结果
     */
    private String result;
}
