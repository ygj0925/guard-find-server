package com.guardfind.server.accounting;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guardfind.server.accounting.mapper.AccountingSnapshotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.UUID;
@Component @RequiredArgsConstructor @Slf4j @EnableScheduling
@ConditionalOnProperty(name="guardfind.accounting.scheduling.enabled",havingValue="true")
public class AccountingRecurringWorker {
 private final AccountingSnapshotMapper mapper;
 private final ObjectMapper json;
 @Scheduled(fixedDelayString="${guardfind.accounting.scheduling.delay-ms:60000}")
 public void run(){
  long cursor=0;for(int batch=0;batch<1000;batch++){
   var snapshots=mapper.pageAfter(cursor);if(snapshots.isEmpty())return;
   for(var snapshot:snapshots){cursor=snapshot.getUserId();try{
    var root=(ObjectNode)json.readTree(snapshot.getPayload());
    String zone=root.path("data").path("settings").path("timezone").asText("Asia/Shanghai");
    if(!AccountingRecurringCalculator.advance(root,LocalDate.now(ZoneId.of(zone))))continue;
    String payload=json.writeValueAsString(root);if(payload.length()>5000000)continue;
    long previous=snapshot.getVersion();snapshot.setVersion(previous+1);snapshot.setPayload(payload);snapshot.setOperationId(UUID.randomUUID().toString());
    mapper.replace(snapshot,previous); // Concurrent app writes win; retry from the latest snapshot next minute.
   }catch(Exception e){log.warn("简账周期任务跳过无效快照 userId={}",snapshot.getUserId());}}
  }
 }
}
