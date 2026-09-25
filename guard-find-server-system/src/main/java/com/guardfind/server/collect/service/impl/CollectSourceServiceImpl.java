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

package com.guardfind.server.collect.service.impl;

import cn.hutool.core.util.StrUtil;
import com.guardfind.server.collect.constant.CollectConstants;
import com.guardfind.server.collect.mapper.CollectSourceMapper;
import com.guardfind.server.collect.model.entity.CollectSourceDO;
import com.guardfind.server.collect.model.query.CollectSourceQuery;
import com.guardfind.server.collect.model.req.CollectSourceReq;
import com.guardfind.server.collect.model.resp.CollectSourceResp;
import com.guardfind.server.collect.service.CollectSourceService;
import com.guardfind.server.common.base.service.BaseServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.continew.starter.core.util.validation.CheckUtils;

import java.util.List;

/**
 * 采集源配置业务实现
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Service
public class CollectSourceServiceImpl extends BaseServiceImpl<CollectSourceMapper, CollectSourceDO, CollectSourceResp, CollectSourceResp, CollectSourceQuery, CollectSourceReq> implements CollectSourceService {

    @Override
    public void beforeCreate(CollectSourceReq req) {
        CheckUtils.throwIfNotEmpty(this.lambdaQuery()
            .eq(CollectSourceDO::getCode, req.getCode())
            .list(), "源标识 [{}] 已存在", req.getCode());
        this.applyDefaults(req);
    }

    @Override
    public void beforeUpdate(CollectSourceReq req, Long id) {
        CollectSourceDO source = super.getById(id);
        CheckUtils.throwIfNull(source, "采集源不存在");
        CheckUtils.throwIf(!StrUtil.equals(source.getCode(), req.getCode()), "源标识不允许修改，否则历史候选与原文快照将无法回溯");
        this.applyDefaults(req);
    }

    @Override
    public List<CollectSourceResp> listEnabled() {
        return this.lambdaQuery()
            .eq(CollectSourceDO::getEnabled, Boolean.TRUE)
            .orderByAsc(CollectSourceDO::getId)
            .list()
            .stream()
            .map(source -> cn.hutool.core.bean.BeanUtil.copyProperties(source, CollectSourceResp.class))
            .toList();
    }

    /**
     * 落库前的合规默认值：未显式配置即取最保守值，且默认关闭
     */
    private void applyDefaults(CollectSourceReq req) {
        req.setCode(StrUtil.trim(req.getCode()));
        req.setBaseUrl(StrUtil.trim(req.getBaseUrl()));
        if (req.getEnabled() == null) {
            req.setEnabled(Boolean.FALSE);
        }
        if (req.getCrawlDelayMs() == null || req.getCrawlDelayMs() < 0) {
            req.setCrawlDelayMs((int)CollectConstants.DEFAULT_CRAWL_DELAY_MILLIS);
        }
        if (req.getMaxPages() == null || req.getMaxPages() <= 0) {
            req.setMaxPages(CollectConstants.DEFAULT_MAX_PAGES);
        }
        if (req.getRequestTimeoutMs() == null || req.getRequestTimeoutMs() <= 0) {
            req.setRequestTimeoutMs(CollectConstants.DEFAULT_TIMEOUT_MILLIS);
        }
    }
}
