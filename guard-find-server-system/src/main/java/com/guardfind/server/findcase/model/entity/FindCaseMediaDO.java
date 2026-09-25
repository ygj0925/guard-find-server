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

package com.guardfind.server.findcase.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.guardfind.server.common.base.model.entity.BaseDO;
import lombok.Data;

import java.io.Serial;

/**
 * 寻人档案媒体实体
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@TableName("gf_find_case_media")
public class FindCaseMediaDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 档案 ID
     */
    private Long caseId;

    /**
     * 类型（PHOTO/VIDEO）
     */
    private String type;

    /**
     * 地址
     */
    private String url;

    /**
     * 缩略图地址
     */
    private String thumbUrl;

    /**
     * sys_file ID
     */
    private Long storageId;

    /**
     * 内容摘要
     */
    private String sha256;

    /**
     * 排序
     */
    private Integer sort;
}
