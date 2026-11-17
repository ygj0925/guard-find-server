package com.guardfind.server.accounting;

import lombok.Data;

@Data
public class AccountingSnapshot {
    private Long userId;
    private Long version;
    private String payload;
    private String operationId;
}
