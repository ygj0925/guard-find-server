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
import com.feiniaojin.gracefulresponse.api.ResponseStatusFactory;
import com.feiniaojin.gracefulresponse.defaults.DefaultResponseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 工具类及响应信封在静态初始化时取 Spring Bean，裸 JVM 测试需先注入无容器夹具。 */
public abstract class ContainerlessTestBase {

    @BeforeEach
    void prepareSpringUtil() {
        if (SpringUtil.getApplicationContext() != null) {
            return;
        }
        var context = mock(ApplicationContext.class);
        var responseStatuses = mock(ResponseStatusFactory.class);
        when(responseStatuses.defaultSuccess()).thenAnswer(invocation -> new DefaultResponseStatus("0", "ok"));
        when(responseStatuses.defaultError()).thenAnswer(invocation -> new DefaultResponseStatus("500", "error"));
        when(context.getBean(ResponseStatusFactory.class)).thenReturn(responseStatuses);
        new SpringUtil().setApplicationContext(context);
    }
}
