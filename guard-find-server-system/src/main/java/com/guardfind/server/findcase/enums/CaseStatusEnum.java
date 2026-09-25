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

import java.util.EnumSet;
import java.util.Set;

/**
 * 寻人档案状态
 *
 * <p>
 * 状态机：DRAFT → PENDING_REVIEW → PUBLISHED → FOUND / ARCHIVED / OFFLINE / BLOCKED / ERASED；
 * REJECTED、WITHDRAWN 可回到 PENDING_REVIEW。对外可见（广场、搜索、公开详情）的状态集合由
 * {@link #isVisiblePublicly()} 统一给出，任何读接口 MUST 以它作为过滤依据，避免散落判断。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public enum CaseStatusEnum {

    /**
     * 草稿
     */
    DRAFT("草稿"),

    /**
     * 待审核
     */
    PENDING_REVIEW("待审核"),

    /**
     * 已发布（对外可见）
     */
    PUBLISHED("已发布"),

    /**
     * 已驳回（可修改后重新提交）
     */
    REJECTED("已驳回"),

    /**
     * 已撤回
     */
    WITHDRAWN("已撤回"),

    /**
     * 已找回
     */
    FOUND("已找回"),

    /**
     * 已归档（超时效自动降级，对外不可见，本人可续期）
     */
    ARCHIVED("已归档"),

    /**
     * 已下架（处置保留数据，对外不可见）
     */
    OFFLINE("已下架"),

    /**
     * 已封禁（违法违规）
     */
    BLOCKED("已封禁"),

    /**
     * 已删除（响应删除权，识别字段已清除）
     */
    ERASED("已删除");

    private final String description;

    CaseStatusEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 对外可见状态集合
     *
     * @return 状态集合
     */
    public static Set<CaseStatusEnum> visiblePublicly() {
        return EnumSet.of(PUBLISHED);
    }

    /**
     * 喜报（已找回）流可见状态集合
     *
     * @return 状态集合
     */
    public static Set<CaseStatusEnum> foundVisible() {
        return EnumSet.of(FOUND);
    }

    /**
     * 判断状态跃迁是否合法
     *
     * @param from 当前状态
     * @param to   目标状态
     * @return true：合法
     */
    public static boolean canTransfer(CaseStatusEnum from, CaseStatusEnum to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        // 删除权优先：任意状态（除已删除）均可转 ERASED
        if (to == ERASED) {
            return from != ERASED;
        }
        Set<CaseStatusEnum> allowed = switch (from) {
            case DRAFT -> EnumSet.of(PENDING_REVIEW, WITHDRAWN);
            case PENDING_REVIEW -> EnumSet.of(PUBLISHED, REJECTED, WITHDRAWN);
            case PUBLISHED -> EnumSet.of(FOUND, ARCHIVED, OFFLINE, BLOCKED, WITHDRAWN);
            case REJECTED -> EnumSet.of(PENDING_REVIEW);
            case WITHDRAWN -> EnumSet.of(PENDING_REVIEW, DRAFT);
            case ARCHIVED -> EnumSet.of(PUBLISHED, FOUND, OFFLINE);
            case FOUND -> EnumSet.of(PUBLISHED);
            case OFFLINE -> EnumSet.of(PUBLISHED, BLOCKED);
            case BLOCKED -> EnumSet.of(PENDING_REVIEW, OFFLINE);
            case ERASED -> Set.of();
        };
        return allowed.contains(to);
    }

    /**
     * 解析状态，忽略大小写
     *
     * @param value 状态值
     * @return 状态，未匹配时为 null
     */
    public static CaseStatusEnum of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (CaseStatusEnum status : values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        return null;
    }
}
