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

package com.guardfind.server.collect.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 采集数据撤回请求参数
 *
 * <p>
 * 站方或权利人要求"把从我这儿采走的数据撤掉"时的入口：按来源编号或来源链接圈定范围，
 * {@code requester} 记录请求方（机构/联系人/工单号），执行结果写 {@code gf_collect_erase_log} 备查。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "采集数据撤回请求参数")
public class CollectEraseReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 采集源标识
     */
    @Schema(description = "采集源标识", example = "rangaihuijia")
    @NotBlank(message = "采集源标识不能为空")
    @Length(max = 32, message = "采集源标识长度不能超过 {max} 个字符")
    private String sourceCode;

    /**
     * 来源编号列表
     */
    @Schema(description = "来源编号列表")
    private List<String> sourceIds;

    /**
     * 来源链接列表
     */
    @Schema(description = "来源链接列表")
    private List<String> sourceUrls;

    /**
     * 请求方
     */
    @Schema(description = "请求方", example = "站方运营（工单 2026-0924-01）")
    @NotBlank(message = "请求方不能为空")
    @Length(max = 128, message = "请求方长度不能超过 {max} 个字符")
    private String requester;

    /**
     * 说明
     */
    @Schema(description = "说明")
    @Length(max = 500, message = "说明长度不能超过 {max} 个字符")
    private String remark;
}
