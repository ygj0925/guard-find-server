package com.guardfind.server.accounting;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import top.continew.starter.ratelimiter.annotation.RateLimiter;
import top.continew.starter.ratelimiter.enums.LimitType;
@RestController @RequiredArgsConstructor @RequestMapping("/app/accounting/receipts")
public class AccountingReceiptController {
 private final AccountingReceiptService service;
 @GetMapping public java.util.List<AccountingReceiptService.Metadata> list(){return service.list();}
 @PostMapping @RateLimiter(name="ACCOUNTING:RECEIPT",rate=10,interval=60,type=LimitType.IP)
 public AccountingReceiptService.Metadata upload(@RequestPart("file")MultipartFile file)throws IOException{return service.upload(file);}
 @GetMapping("/{id}")public void get(@PathVariable String id,jakarta.servlet.http.HttpServletResponse response)throws IOException{
  var receipt=service.get(id);
  var disposition=ContentDisposition.attachment().filename(receipt.getName(),StandardCharsets.UTF_8).build();
  response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");response.setHeader("X-Content-Type-Options","nosniff");response.setHeader(HttpHeaders.CONTENT_DISPOSITION,disposition.toString());response.setContentType(receipt.getMime());response.getOutputStream().write(receipt.getContent());response.flushBuffer();
 }
 @DeleteMapping("/{id}")public void remove(@PathVariable String id){service.remove(id);}
}
