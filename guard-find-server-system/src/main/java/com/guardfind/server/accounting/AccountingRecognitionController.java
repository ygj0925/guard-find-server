package com.guardfind.server.accounting;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.exception.AppBizException;
import com.guardfind.server.ledger.support.LedgerCategories;
import com.guardfind.server.ledger.enums.LedgerTypeEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import top.continew.starter.ratelimiter.annotation.RateLimiter;
import top.continew.starter.ratelimiter.enums.LimitType;
@RestController @RequiredArgsConstructor @RequestMapping("/app/accounting")
public class AccountingRecognitionController {
 private final AccountingRecognitionGateway gateway;
 private final AccountingReceiptService receipts;
 public record RecognitionReq(String receiptId,@Size(max=4000)String text,String date){}
 public record Draft(int type,BigDecimal amount,String category,String recordDate,String remark){}
 @PostMapping("/recognize") @RateLimiter(name="ACCOUNTING:RECOGNIZE",rate=10,interval=60,type=LimitType.IP)
 public Draft recognize(@RequestBody @Valid RecognitionReq req){
  AppUserContextHolder.getUserIdRequired();
  if((req.text()==null||req.text().isBlank())&&(req.receiptId()==null||req.receiptId().isBlank()))throw rejected("请选择票据或输入消费描述");
  var receipt=req.receiptId()==null?null:receipts.get(req.receiptId());
  String date=req.date()==null?LocalDate.now().toString():req.date();try{LocalDate.parse(date);}catch(Exception e){throw rejected("日期无效");}
  var result=gateway.recognize(req.text(),receipt,date);
  try{
   int type=result.path("type").asInt();var amount=new BigDecimal(result.path("amount").asText());String category=result.path("category").asText(),recordDate=result.path("recordDate").asText(),remark=result.path("remark").asText();
   if((type!=1&&type!=2)||amount.signum()<=0||amount.scale()>2||amount.compareTo(new BigDecimal("999999999.99"))>0||!LedgerCategories.isValid(LedgerTypeEnum.of(type),category)||remark.length()>500)throw rejected("识别结果不完整，请手动录入");
   LocalDate.parse(recordDate);return new Draft(type,amount,category,recordDate,remark);
  }catch(AppBizException e){throw e;}catch(Exception e){throw rejected("未识别到确定的金额和日期，请手动录入");}
 }
 @PostMapping("/transcribe") @RateLimiter(name="ACCOUNTING:TRANSCRIBE",rate=10,interval=60,type=LimitType.IP)
 public Map<String,String> transcribe(@RequestPart("file")MultipartFile file)throws java.io.IOException{
  AppUserContextHolder.getUserIdRequired();if(file==null||file.isEmpty()||file.getSize()>10*1024*1024||!Set.of("audio/mp4","audio/m4a","audio/x-m4a","audio/mpeg","audio/wav","audio/webm").contains(file.getContentType()))throw rejected("音频需为10MB以内的MP4/M4A/MP3/WAV/WebM");return Map.of("text",gateway.transcribe(file.getBytes(),file.getContentType()));
 }
 private AppBizException rejected(String message){return new AppBizException(AppBizException.RULE_REJECTED,message);}
}
