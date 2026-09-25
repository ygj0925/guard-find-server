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

package com.guardfind.server.support;

import cn.hutool.extra.spring.SpringUtil;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationContext;

import static org.mockito.Mockito.mock;

/**
 * 无容器单元测试基类（不加载 Spring 上下文，不依赖 MySQL/Redis/网络）
 *
 * <p>
 * ContiNew 的 {@code ValidationUtils}（父类 {@code Validator}）与 {@code RedisUtils} 在类初始化阶段
 * 通过 Hutool {@code SpringUtil.getBean(Class)} 取 {@code jakarta.validation.Validator}、
 * {@code RedissonClient}，裸 JVM 下会抛 {@code ExceptionInInitializerError}，
 * 进而导致 {@code mockStatic(RedisUtils.class)} 报 "Cannot instrument class"。
 * 本基类仅向 {@code SpringUtil} 注入一个 Mock 的 {@code ApplicationContext}（不启动容器、不做任何真实解析），
 * 使上述工具类完成类初始化；Redis 的读写行为仍由各测试用 {@code mockStatic} 自行打桩。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/25
 */
public abstract class ContainerlessTestBase {

    @BeforeEach
    void prepareSpringUtil() {
        if (SpringUtil.getApplicationContext() != null) {
            return;
        }
        // Mock 上下文对所有 getBean 调用返回 null，足以让 static final 字段完成赋值
        new SpringUtil().setApplicationContext(mock(ApplicationContext.class));
    }
}
