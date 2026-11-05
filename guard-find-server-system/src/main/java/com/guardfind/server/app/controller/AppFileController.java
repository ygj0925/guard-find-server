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

package com.guardfind.server.app.controller;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.exception.AppBizException;
import com.guardfind.server.system.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.dromara.x.file.storage.core.FileInfo;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Date;
import java.util.Set;

/**
 * C 端图片上传。目录按用户与业务域隔离,仅允许图片扩展名与 MIME,单张上限 5MB。
 *
 * <p>
 * 文件最终落在公开静态目录,路径隔离用于审计与清理,不作为访问控制;敏感材料不得走此接口。
 * </p>
 */
@Tag(name = "C 端文件上传 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/file")
public class AppFileController {

    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_PATHS = Set.of("case", "lead", "avatar", "report", "post");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final FileService fileService;

    @Operation(summary = "上传图片", description = "仅登录用户;path 取 case/lead/avatar/report/post;JPG/PNG/WebP/GIF,单张不超过 5MB")
    @Parameter(name = "path", description = "业务目录", example = "avatar")
    @PostMapping("/upload")
    public AppUploadResp upload(@RequestPart("file") MultipartFile file,
                                @RequestParam(defaultValue = "case") String path) throws IOException {
        Long userId = AppUserContextHolder.getUserIdRequired();
        String folder = StrUtil.trimToEmpty(path).toLowerCase();
        if (!ALLOWED_PATHS.contains(folder)) {
            throw new AppBizException(AppBizException.RULE_REJECTED, "不支持的上传目录");
        }
        if (file == null || file.isEmpty()) {
            throw new AppBizException(AppBizException.RULE_REJECTED, "文件不能为空");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new AppBizException(AppBizException.RULE_REJECTED, "图片不能超过 5MB");
        }
        String contentType = StrUtil.nullToEmpty(file.getContentType()).toLowerCase();
        String ext = StrUtil.nullToEmpty(FileUtil.extName(file.getOriginalFilename())).toLowerCase();
        if (!contentType.startsWith("image/") || !ALLOWED_EXTENSIONS.contains(ext)) {
            throw new AppBizException(AppBizException.RULE_REJECTED, "仅支持 JPG/PNG/WebP/GIF 图片");
        }
        String parentPath = StrUtil.format("app/{}/{}/{}", userId, folder, DateUtil.format(new Date(), "yyyy/MM/dd"));
        FileInfo info = fileService.upload(file, parentPath);
        long size = info.getSize() == null ? file.getSize() : info.getSize();
        return new AppUploadResp(info.getUrl(), info.getOriginalFilename(), size);
    }

    public record AppUploadResp(String url, String name, long size) {}
}
