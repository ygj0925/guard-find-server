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

package com.guardfind.server.im;

import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.exception.AppBizException;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.im.mapper.*;
import com.guardfind.server.im.model.entity.ImConversationDO;
import com.guardfind.server.im.service.impl.ImServiceImpl;
import com.guardfind.server.notification.service.AppMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImAppBoundaryTest {
    @Test
    void strangerCannotClearOthersUnreadMessages() {
        var conversations = mock(ImConversationMapper.class);
        var messages = mock(ImMessageMapper.class);
        var service = new ImServiceImpl(conversations, messages, mock(AppUserMapper.class), mock(AppMessageService.class), mock(AdminMessageMapper.class), mock(ObjectProvider.class));
        var c = new ImConversationDO();
        c.setId(10L);
        c.setUserAId(2L);
        c.setUserBId(3L);
        when(conversations.selectById(10L)).thenReturn(c);
        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(1L);
            assertThrows(AppBizException.class, () -> service.markRead(10L));
            verifyNoInteractions(messages);
            verify(conversations, never()).update(isNull(), any());
        }
    }

    @Test
    void deleteDoesNotChangePeerSummary() {
        var conversations = mock(ImConversationMapper.class);
        var service = new ImServiceImpl(conversations, mock(ImMessageMapper.class), mock(AppUserMapper.class), mock(AppMessageService.class), mock(AdminMessageMapper.class), mock(ObjectProvider.class));
        var c = new ImConversationDO();
        c.setId(10L);
        c.setUserAId(1L);
        c.setUserBId(2L);
        when(conversations.selectById(10L)).thenReturn(c);
        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(1L);
            service.delete(10L);
            var cap = org.mockito.ArgumentCaptor
                .forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
            verify(conversations).update(isNull(), cap.capture());
            assertFalse(cap.getValue().getSqlSet().contains("last_message"));
        }
    }
}
