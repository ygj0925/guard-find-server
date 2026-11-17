package com.guardfind.server.accounting;
import com.guardfind.server.accounting.mapper.AccountingSnapshotMapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.exception.AppBizException;
import com.guardfind.server.support.ContainerlessTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountingSnapshotServiceTest extends ContainerlessTestBase {
    AccountingSnapshotMapper mapper;
    AccountingSnapshotService service;
    String payload = """
        {"format":"jianzhang","version":1,"data":{"activeBookId":"b","books":[{"id":"b"}],"accounts":[],"categories":[],"entries":[],"recurring":[],"budgets":[],"settings":{}}}
        """;
    String operation = "operation-123456789";
    @BeforeEach void setup() { mapper = mock(AccountingSnapshotMapper.class); service = new AccountingSnapshotService(mapper, new ObjectMapper()); }
    @Test void noSnapshotReturnsVersionZero() {
        try (var auth = mockStatic(AppUserContextHolder.class)) {
            auth.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            assertEquals(0, service.get().version()); verify(mapper).find(7L);
        }
    }
    @Test void newSnapshotUsesAuthenticatedOwner() {
        try (var auth = mockStatic(AppUserContextHolder.class)) {
            auth.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            assertEquals(1, service.save(0, operation, payload).version());
            var capture = ArgumentCaptor.forClass(AccountingSnapshot.class); verify(mapper).insertSnapshot(capture.capture());
            assertEquals(7L, capture.getValue().getUserId());
        }
    }
    @Test void invalidPayloadNeverWrites() {
        try (var auth = mockStatic(AppUserContextHolder.class)) {
            auth.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            assertThrows(AppBizException.class, () -> service.save(0, operation, "{}"));
            verify(mapper, never()).insertSnapshot(any());
        }
    }
    @Test void staleVersionCannotOverwrite() {
        try (var auth = mockStatic(AppUserContextHolder.class)) {
            auth.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            var existing = new AccountingSnapshot(); existing.setVersion(2L); when(mapper.find(7L)).thenReturn(existing);
            var ex = assertThrows(AppBizException.class, () -> service.save(1, operation, payload));
            assertEquals(AppBizException.STATE_CONFLICT, ex.getCode()); verify(mapper, never()).replace(any(), anyLong());
        }
    }
    @Test void sameOperationRetryIsIdempotent() {
        try (var auth = mockStatic(AppUserContextHolder.class)) {
            auth.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            var existing = new AccountingSnapshot(); existing.setVersion(2L); existing.setOperationId(operation); existing.setPayload(payload); when(mapper.find(7L)).thenReturn(existing);
            assertEquals(2, service.save(1, operation, payload).version()); verify(mapper, never()).replace(any(), anyLong());
        }
    }
    @Test void concurrentCasFailureIsConflict() {
        try (var auth = mockStatic(AppUserContextHolder.class)) {
            auth.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            var existing = new AccountingSnapshot(); existing.setVersion(1L); when(mapper.find(7L)).thenReturn(existing);
            assertThrows(AppBizException.class, () -> service.save(1, operation, payload));
        }
    }
}
