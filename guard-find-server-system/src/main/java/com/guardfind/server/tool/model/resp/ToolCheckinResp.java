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

package com.guardfind.server.tool.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 签到活动响应
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Data
@Schema(description = "签到活动响应")
public class ToolCheckinResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "活动 ID")
    private Long id;

    @Schema(description = "签到主题")
    private String title;

    @Schema(description = "活动地点名(可空)")
    private String locationName;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "截止时间")
    private LocalDateTime endTime;

    @Schema(description = "当前是否在时间窗内")
    private Boolean inWindow;

    @Schema(description = "已签到人数")
    private Long signedCount;

    @Schema(description = "动态码种子(仅发起人可见;客户端本地按 30 秒轮换计算展示)")
    private String secret;

    @Schema(description = "当前用户是否已签到")
    private Boolean signedByMe;

    @Schema(description = "当前用户签到时填写的地点")
    private String myLocationName;

    @Schema(description = "签到名单(仅发起人可见)")
    private List<CheckinRecord> records;

    /**
     * 签到记录条目
     */
    @Data
    @Schema(description = "签到记录条目")
    public static class CheckinRecord implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "签到人用户 ID")
        private Long userId;

        @Schema(description = "签到人昵称")
        private String nickname;

        @Schema(description = "签到人头像")
        private String avatar;

        @Schema(description = "签到人填写的所在地点")
        private String locationName;

        @Schema(description = "签到时间")
        private LocalDateTime createTime;
    }
}
