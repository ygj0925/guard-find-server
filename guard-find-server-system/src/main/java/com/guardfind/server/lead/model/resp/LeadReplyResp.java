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

package com.guardfind.server.lead.model.resp;

import com.guardfind.server.lead.enums.LeadReplyFromEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 线索回复视图
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "线索回复视图")
public class LeadReplyResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 回复 ID
     */
    @Schema(description = "回复 ID", example = "1802000000000000001")
    private Long id;

    /**
     * 回复内容
     */
    @Schema(description = "回复内容", example = "请提供当时的照片")
    private String content;

    /**
     * 发起方（OWNER/SUBMITTER）
     */
    @Schema(description = "发起方（OWNER/SUBMITTER）", example = "OWNER")
    private LeadReplyFromEnum from;

    /**
     * 回复时间
     */
    @Schema(description = "回复时间", example = "2026-09-24 11:20:00", type = "string")
    private LocalDateTime time;

    /**
     * 构造回复视图
     *
     * @param id      回复 ID
     * @param content 内容
     * @param from    发起方
     * @param time    时间
     * @return 回复视图
     */
    public static LeadReplyResp of(Long id, String content, LeadReplyFromEnum from, LocalDateTime time) {
        LeadReplyResp resp = new LeadReplyResp();
        resp.setId(id);
        resp.setContent(content);
        resp.setFrom(from);
        resp.setTime(time);
        return resp;
    }
}
