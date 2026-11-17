package com.guardfind.server.accounting;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guardfind.server.accounting.mapper.*;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.exception.AppBizException;
import com.guardfind.server.support.ContainerlessTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.List;
class AccountingRemainingTest extends ContainerlessTestBase {
 final ObjectMapper json=new ObjectMapper();
 @Test void patchAppliesIncrementAndRetryIsIdempotent()throws Exception{
  var mapper=mock(AccountingSnapshotMapper.class);var service=new AccountingSnapshotService(mapper,json);var snapshot=new AccountingSnapshot();snapshot.setVersion(1L);snapshot.setOperationId("previous-operation");snapshot.setUserId(7L);snapshot.setPayload("""
  {"format":"jianzhang","version":1,"data":{"activeBookId":"daily","books":[{"id":"daily","name":"日常"}],"accounts":[],"categories":[],"entries":[],"budgets":[],"recurring":[],"settings":{"billingStartDay":1,"hideAmounts":false,"theme":"light","accent":"#E9B949"}}}
  """);
  try(var auth=mockStatic(AppUserContextHolder.class)){
   auth.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);when(mapper.find(7L)).thenReturn(snapshot);when(mapper.replace(any(),eq(1L))).thenReturn(1);
   var changes=List.of(new AccountingSnapshotController.Change("settings","hideAmounts",json.getNodeFactory().booleanNode(true)));
   var result=service.patch(1,"operation-id-123456",changes);assertEquals(2,result.version());assertTrue(json.readTree(result.payload()).path("data").path("settings").path("hideAmounts").asBoolean());
   snapshot.setPayload(result.payload());snapshot.setVersion(2L);snapshot.setOperationId("operation-id-123456");
   assertEquals(2,service.patch(1,"operation-id-123456",changes).version());verify(mapper,times(1)).replace(any(),anyLong());
  }
 }
 @Test void recurringClampsAndIsIdempotent()throws Exception{
  var root=(ObjectNode)json.readTree("""
   {"data":{"entries":[],"recurring":[{"id":"rule","enabled":true,"nextDate":"2024-01-31","day":31,"frequency":"monthly","template":{"bookId":"daily","cents":100}}]}}
   """);
  assertTrue(AccountingRecurringCalculator.advance(root,LocalDate.parse("2024-03-31")));assertEquals(3,root.path("data").path("entries").size());assertEquals("2024-02-29",root.path("data").path("entries").path(1).path("date").asText());assertFalse(AccountingRecurringCalculator.advance(root,LocalDate.parse("2024-03-31")));
 }
 @Test void receiptValidatesMagicAndOwner()throws Exception{
  var mapper=mock(AccountingReceiptMapper.class);var service=new AccountingReceiptService(mapper);
  try(var auth=mockStatic(AppUserContextHolder.class)){
   auth.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
   var fake=new MockMultipartFile("file","secret.png","image/png","<script>".getBytes());assertThrows(AppBizException.class,()->service.upload(fake));verify(mapper,never()).insertReceipt(any());
   var pdf=new MockMultipartFile("file","invoice.pdf","application/pdf","%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII));var saved=service.upload(pdf);assertEquals("application/pdf",saved.mime());
   assertThrows(AppBizException.class,()->service.get("other-users-file"));verify(mapper).find("other-users-file",7L);
  }
 }
 @Test void invalidRecognitionNeverCreatesRecords()throws Exception{
  var gateway=mock(AccountingRecognitionGateway.class);var service=mock(AccountingReceiptService.class);var controller=new AccountingRecognitionController(gateway,service);
  try(var auth=mockStatic(AppUserContextHolder.class)){
   auth.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
   when(gateway.recognize(any(),isNull(),any())).thenReturn(json.readTree("{\"type\":1,\"amount\":null}"));assertThrows(AppBizException.class,()->controller.recognize(new AccountingRecognitionController.RecognitionReq(null,"午饭", "2026-11-17")));
  }
 }
 @Test void patchRejectsUnknownCollectionAndStaleVersion()throws Exception{
  var mapper=mock(AccountingSnapshotMapper.class);var service=new AccountingSnapshotService(mapper,json);var snapshot=new AccountingSnapshot();snapshot.setVersion(2L);snapshot.setOperationId("existing-operation");snapshot.setPayload("{\"format\":\"jianzhang\",\"version\":1,\"data\":{}}");
  try(var auth=mockStatic(AppUserContextHolder.class)){
   auth.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);when(mapper.find(7L)).thenReturn(snapshot);
   assertThrows(AppBizException.class,()->service.patch(1,"new-operation-1234",List.of(new AccountingSnapshotController.Change("entries","e",json.createObjectNode()))));
   assertThrows(AppBizException.class,()->service.patch(2,"new-operation-1234",List.of(new AccountingSnapshotController.Change("userId","7",json.createObjectNode()))));verify(mapper,never()).replace(any(),anyLong());
  }
 }
}
