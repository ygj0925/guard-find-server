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

package com.guardfind.server.app;

import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.controller.AppFileController;
import com.guardfind.server.app.exception.AppBizException;
import com.guardfind.server.support.ContainerlessTestBase;
import com.guardfind.server.system.service.FileService;
import org.dromara.x.file.storage.core.FileInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AppFileController} 单测:登录归属、目录白名单、图片类型与大小限制。
 */
class AppFileControllerTest extends ContainerlessTestBase {

    private FileService fileService;
    private AppFileController controller;

    @BeforeEach
    void setUp() {
        fileService = mock(FileService.class);
        controller = new AppFileController(fileService);
    }

    private static AppBizException rejected(Executable action) {
        return assertThrows(AppBizException.class, action);
    }

    @Test
    @DisplayName("上传成功:目录按用户与业务域隔离,返回 url/name/size")
    void uploadScopesPathByUserAndFolder() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.PNG", "image/png", new byte[]{1, 2, 3});
        FileInfo info = mock(FileInfo.class);
        when(info.getUrl()).thenReturn("https://cdn.example.com/a.png");
        when(info.getOriginalFilename()).thenReturn("a.png");
        when(info.getSize()).thenReturn(3L);
        when(fileService.upload(any(MultipartFile.class), anyString())).thenReturn(info);

        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            var resp = controller.upload(file, " Avatar ");

            ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
            verify(fileService).upload(any(MultipartFile.class), pathCaptor.capture());
            String path = pathCaptor.getValue();
            assertTrue(path.startsWith("app/7/avatar/"), path);
            assertTrue(path.matches("app/7/avatar/\\d{4}/\\d{2}/\\d{2}"), path);
            assertEquals("https://cdn.example.com/a.png", resp.url());
            assertEquals("a.png", resp.name());
            assertEquals(3L, resp.size());
        }
    }

    @Test
    @DisplayName("非图片扩展名或 MIME 不匹配时拒绝,不落存储")
    void rejectsNonImage() throws Exception {
        MockMultipartFile text = new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{1});
        MockMultipartFile spoofed = new MockMultipartFile("file", "a.png", "text/plain", new byte[]{1});

        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            assertEquals(AppBizException.RULE_REJECTED,
                rejected(() -> controller.upload(text, "case")).getCode());
            assertEquals(AppBizException.RULE_REJECTED,
                rejected(() -> controller.upload(spoofed, "case")).getCode());
            verify(fileService, never()).upload(any(MultipartFile.class), anyString());
        }
    }

    @Test
    @DisplayName("超过 5MB 拒绝")
    void rejectsOversize() throws Exception {
        byte[] bytes = new byte[5 * 1024 * 1024 + 1];
        Arrays.fill(bytes, (byte) 1);
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", bytes);

        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            assertEquals(AppBizException.RULE_REJECTED,
                rejected(() -> controller.upload(file, "case")).getCode());
            verify(fileService, never()).upload(any(MultipartFile.class), anyString());
        }
    }

    @Test
    @DisplayName("未知目录与空文件均拒绝")
    void rejectsUnknownFolderAndEmptyFile() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);
        MockMultipartFile ok = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});

        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            assertEquals(AppBizException.RULE_REJECTED,
                rejected(() -> controller.upload(ok, "private")).getCode());
            assertEquals(AppBizException.RULE_REJECTED,
                rejected(() -> controller.upload(empty, "case")).getCode());
            verify(fileService, never()).upload(any(MultipartFile.class), anyString());
        }
    }

    @Test
    @DisplayName("未登录直接返回 401,不读取文件")
    void rejectsUnauthenticated() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});
        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired)
                .thenThrow(new AppBizException(AppBizException.UNAUTHORIZED, "登录已过期，请重新登录"));

            AppBizException ex = assertThrows(AppBizException.class, () -> controller.upload(file, "avatar"));
            assertEquals(AppBizException.UNAUTHORIZED, ex.getCode());
            verify(fileService, never()).upload(any(MultipartFile.class), anyString());
        }
    }
}
