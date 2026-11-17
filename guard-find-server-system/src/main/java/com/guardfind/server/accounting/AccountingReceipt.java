package com.guardfind.server.accounting;
import lombok.Data;
@Data
public class AccountingReceipt {
    private String id;
    private Long userId;
    private String name;
    private String mime;
    private Long size;
    private byte[] content;
}
