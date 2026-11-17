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

package com.guardfind.server.findcase;

import com.guardfind.server.findcase.support.CaseMediaFiles;
import com.guardfind.server.system.mapper.FileMapper;
import com.guardfind.server.system.mapper.StorageMapper;
import com.guardfind.server.system.model.entity.FileDO;
import com.guardfind.server.system.model.entity.StorageDO;
import com.guardfind.server.support.ContainerlessTestBase;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class CaseMediaFilesTest extends ContainerlessTestBase {
    @Test
    void resolvesOldUrlsButRejectsOtherOwnersFoldersAndHosts() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), FileDO.class);
        var files = mock(FileMapper.class);
        var storages = mock(StorageMapper.class);
        var file = new FileDO();
        file.setId(99L);
        file.setName("a.png");
        file.setParentPath("/app/7/case/2026/11/17");
        file.setPath("/app/7/case/2026/11/17/a.png");
        file.setStorageId(12L);
        var storage = mock(StorageDO.class);
        when(storage.getUrlPrefix()).thenReturn("https://cdn.test/static/");
        when(files.selectList(any())).thenReturn(List.of(file));
        when(storages.selectById(12L)).thenReturn(storage);
        var resolver = new CaseMediaFiles(files, storages);
        String url = "https://cdn.test/static/app/7/case/2026/11/17/a.png";
        assertThat(resolver.requireOwned(url, 7L)).isEqualTo(99L);
        assertThat(resolver.findOwned(url.replace("cdn.test", "evil.test"), 7L)).isNull();
        assertThatThrownBy(() -> resolver.requireOwned(url, 8L)).hasMessageContaining("本人上传");
        file.setParentPath("/app/7/avatar/2026/11/17");
        assertThat(resolver.findOwned(url, 7L)).isNull();
    }
}
