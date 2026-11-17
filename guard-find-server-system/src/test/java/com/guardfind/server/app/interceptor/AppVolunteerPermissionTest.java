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

package com.guardfind.server.app.interceptor;

import com.guardfind.server.app.context.AppUserContext;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.app.service.AppUserService;
import com.guardfind.server.app.util.StpAppUtil;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.session.SaSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppVolunteerPermissionTest {
    @Test
    @SuppressWarnings("unchecked")
    void selfDeclaredAndLegacySessionsAreDenied() {
        var context = new AppUserContext();
        context.setId(7L);
        context.setIdentityType("VOLUNTEER");
        var local = (ThreadLocal<AppUserContext>) ReflectionTestUtils.getField(AppUserContextHolder.class, "CONTEXT_HOLDER");
        local.set(context);
        try {
            assertFalse(AppUserContextHolder.isVolunteer());
            context.setVolunteerVerified(false);
            assertFalse(AppUserContextHolder.isVolunteer());
            context.setVolunteerVerified(true);
            assertTrue(AppUserContextHolder.isVolunteer());
            context.setIdentityType("PUBLIC");
            assertFalse(AppUserContextHolder.isVolunteer());
        } finally { AppUserContextHolder.clear(); }
    }

    @Test
    @SuppressWarnings("unchecked")
    void reloadsCertifiedSessionsSoRevocationIsEffectiveOnNextRequest() {
        var cached = new AppUserContext();
        cached.setId(7L);
        cached.setIdentityType("VOLUNTEER");
        cached.setVolunteerVerified(true);
        var fresh = new AppUserContext();
        fresh.setId(7L);
        fresh.setIdentityType("VOLUNTEER");
        fresh.setVolunteerVerified(false);
        var users = mock(AppUserService.class);
        var user = new AppUserDO();
        when(users.getById(7L)).thenReturn(user);
        when(users.buildContext(user)).thenReturn(fresh);
        var provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(users);
        var logic = mock(StpLogic.class);
        var session = mock(SaSession.class);
        when(logic.getTokenSessionByToken("token")).thenReturn(session);
        when(session.getModel(AppUserContextHolder.CONTEXT_KEY, AppUserContext.class)).thenReturn(cached);
        try (var stp = mockStatic(StpAppUtil.class)) {
            stp.when(StpAppUtil::stpLogic).thenReturn(logic);
            var interceptor = new AppAuthInterceptor(provider);
            assertSame(fresh, interceptor.resolveContext("token", 7L));
            when(users.getById(7L)).thenThrow(new IllegalStateException("unavailable"));
            assertNull(interceptor.resolveContext("token", 7L).getVolunteerVerified());
        }
    }
}
