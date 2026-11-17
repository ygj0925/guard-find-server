package com.guardfind.server.accounting;
import com.guardfind.server.accounting.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component @RequiredArgsConstructor @EnableScheduling
@ConditionalOnProperty(name="guardfind.accounting.retention.enabled",havingValue="true")
public class AccountingRetentionWorker {
 private final AccountingSnapshotMapper snapshots;
 private final AccountingReceiptMapper receipts;
 @Scheduled(cron="${guardfind.accounting.retention.cron:0 0 4 * * *}")
 @Transactional(rollbackFor=Exception.class)
 public void run(){receipts.deleteCancelledUsers();snapshots.deleteCancelledUsers();}
}
