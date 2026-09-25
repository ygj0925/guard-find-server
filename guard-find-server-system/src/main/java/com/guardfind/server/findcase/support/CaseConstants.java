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

package com.guardfind.server.findcase.support;

/**
 * 寻人档案域常量
 *
 * @author guardfind
 * @since 2026/9/24
 */
public final class CaseConstants {

    /**
     * 站内消息业务类型
     */
    public static final String MESSAGE_BIZ_TYPE = "FIND_CASE";

    /**
     * 目标类型（收藏、埋点）
     */
    public static final String TARGET_TYPE_CASE = "CASE";

    /**
     * 档案来源：C 端发布
     */
    public static final String SOURCE_TYPE_APP = "APP";

    /**
     * 媒体类型：图片
     */
    public static final String MEDIA_TYPE_PHOTO = "PHOTO";

    /**
     * 媒体类型：视频
     */
    public static final String MEDIA_TYPE_VIDEO = "VIDEO";

    /**
     * 联络方式：站内（不公开号码）
     */
    public static final String CONTACT_MODE_IN_APP = "IN_APP";

    /**
     * 联络方式：电话（需公开号码）
     */
    public static final String CONTACT_MODE_PHONE = "PHONE";

    /**
     * 档案编号前缀
     */
    public static final String CASE_NO_PREFIX = "WG";

    /**
     * 已删除档案的姓名占位（清除识别字段后仍需满足非空约束）
     */
    public static final String ERASED_NAME = "已删除档案";

    /**
     * 发布时效（天）：超过该时长无人确认则自动归档
     */
    public static final int EXPIRY_DAYS = 90;

    /**
     * 归档前提醒提前量（天）
     */
    public static final int EXPIRY_NOTICE_ADVANCE_DAYS = 7;

    /**
     * 找回喜报播报的收藏用户上限（避免一次任务发出过多消息）
     */
    public static final int FOUND_NOTICE_FAVORITE_LIMIT = 200;

    /**
     * 系统操作人 ID（状态日志中的 SYSTEM 类型）
     */
    public static final Long SYSTEM_OPERATOR_ID = 0L;

    /**
     * 单个搜索词最大长度
     */
    public static final int MAX_KEYWORD_LENGTH = 50;

    /**
     * 信息流默认条数
     */
    public static final int DEFAULT_FEED_SIZE = 10;

    /**
     * 信息流最大条数
     */
    public static final int MAX_FEED_SIZE = 50;

    private CaseConstants() {
    }
}
