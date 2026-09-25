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

package com.guardfind.server.findcase.enums;

/**
 * 埋点事件类型（对应 `gf_user_track.event_type`）
 *
 * @author guardfind
 * @since 2026/9/24
 */
public enum TrackEventEnum {

    /**
     * 卡片曝光
     */
    EXPOSE("曝光"),

    /**
     * 卡片点击
     */
    CLICK("点击"),

    /**
     * 分享
     */
    SHARE("分享"),

    /**
     * 发布
     */
    PUBLISH("发布"),

    /**
     * 查看完整档案（敏感访问留痕）
     */
    VIEW_FULL("查看完整档案");

    private final String description;

    TrackEventEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 解析事件类型，忽略大小写
     *
     * @param value 事件编码
     * @return 事件类型，未匹配时为 null
     */
    public static TrackEventEnum of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (TrackEventEnum item : values()) {
            if (item.name().equalsIgnoreCase(value)) {
                return item;
            }
        }
        return null;
    }
}
