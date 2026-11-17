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

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guardfind.server.app.exception.AppBizException;
import com.guardfind.server.system.mapper.FileMapper;
import com.guardfind.server.system.mapper.StorageMapper;
import com.guardfind.server.system.model.entity.FileDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolve URLs through owned upload records, including media saved by older clients. */
@Component
@RequiredArgsConstructor
public class CaseMediaFiles {
    private final FileMapper files;
    private final StorageMapper storages;

    public Long requireOwned(String url, Long ownerId) {
        Long id = findOwned(url, ownerId);
        if (id == null) {
            throw new AppBizException(AppBizException.RULE_REJECTED, "请使用本人上传的寻人图片");
        }
        return id;
    }

    public Long findOwned(String url, Long ownerId) {
        if (StrUtil.isBlank(url) || ownerId == null) return null;
        String folder = "/app/" + ownerId + "/case/";
        for (FileDO file : files.selectList(Wrappers.<FileDO>lambdaQuery()
            .eq(FileDO::getName, StrUtil.subAfter(url, '/', true))
            .likeRight(FileDO::getParentPath, folder))) {
            if (file.getParentPath() == null || !file.getParentPath().startsWith(folder) || file.getPath() == null) continue;
            String stored = StrUtil.removePrefix(file.getPath(), "/");
            String canonical = stored;
            if (!stored.startsWith("https://") && !stored.startsWith("http://")) {
                var storage = storages.selectById(file.getStorageId());
                if (storage == null || StrUtil.isBlank(storage.getUrlPrefix())) continue;
                canonical = URLUtil.normalize(StrUtil.appendIfMissing(storage.getUrlPrefix(), "/") + stored, false, true);
            }
            if (url.equals(canonical)) return file.getId();
        }
        return null;
    }
}
