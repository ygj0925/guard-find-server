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

package com.guardfind.server.ai.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.guardfind.server.common.base.model.entity.BaseDO;
import lombok.Data;

import java.io.Serial;

/**
 * AI 会话实体
 *
 * @author guardfind
 * @since 2026/10/28
 */
@Data
@TableName("gf_ai_conversation")
public class AiConversationDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属用户 ID
     */
    private Long userId;

    /**
     * 会话标题(默认取首条消息摘要)
     */
    private String title;

    /**
     * 对话模型标识(服务端配置的默认模型)
     */
    private String model;
}
