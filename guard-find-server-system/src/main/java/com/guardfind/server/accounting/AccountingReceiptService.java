package com.guardfind.server.accounting;
import com.guardfind.server.accounting.mapper.AccountingReceiptMapper;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.exception.AppBizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class AccountingReceiptService {
 private final AccountingReceiptMapper mapper;
 public record Metadata(String id,String name,String mime,long size) {}
 public java.util.List<Metadata> list(){return mapper.list(AppUserContextHolder.getUserIdRequired()).stream().map(file->new Metadata(file.getId(),file.getName(),file.getMime(),file.getSize())).toList();}
 public Metadata upload(MultipartFile file)throws IOException{
  Long userId=AppUserContextHolder.getUserIdRequired();
  if(file==null||file.isEmpty()||file.getSize()>5*1024*1024)throw rejected("票据需为1字节至5MB的图片或PDF");
  byte[] bytes=file.getBytes();String mime=detectMime(bytes);
  if(!mime.equals(file.getContentType()))throw rejected("文件内容与声明类型不一致");
  if(mapper.bytesForUser(userId)+bytes.length>100L*1024*1024)throw rejected("票据总空间已达100MB，请清理不需要的票据");
  var receipt=new AccountingReceipt();receipt.setId(UUID.randomUUID().toString());receipt.setUserId(userId);
  String original=file.getOriginalFilename()==null?"票据":file.getOriginalFilename();
  receipt.setName(original.replaceAll("[\\\\/\\r\\n\\x00-\\x1f]","_").substring(0,Math.min(120,original.length())));
  receipt.setMime(mime);receipt.setSize((long)bytes.length);receipt.setContent(bytes);mapper.insertReceipt(receipt);
  return new Metadata(receipt.getId(),receipt.getName(),mime,bytes.length);
 }
 public AccountingReceipt get(String id){var receipt=mapper.find(id,AppUserContextHolder.getUserIdRequired());if(receipt==null)throw new AppBizException(AppBizException.NOT_FOUND,"票据不存在");return receipt;}
 public void remove(String id){Long userId=AppUserContextHolder.getUserIdRequired();if(mapper.remove(id,userId)!=1)throw new AppBizException(AppBizException.NOT_FOUND,"票据不存在");}
 public static String detectMime(byte[] bytes){
  if(bytes.length>=8&&bytes[0]==(byte)0x89&&new String(bytes,1,7,StandardCharsets.ISO_8859_1).equals("PNG\r\n\u001a\n"))return"image/png";
  if(bytes.length>=3&&bytes[0]==(byte)0xff&&bytes[1]==(byte)0xd8&&bytes[2]==(byte)0xff)return"image/jpeg";
  if(bytes.length>=12&&new String(bytes,0,4,StandardCharsets.US_ASCII).equals("RIFF")&&new String(bytes,8,4,StandardCharsets.US_ASCII).equals("WEBP"))return"image/webp";
  if(bytes.length>=5&&new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))return"application/pdf";
  throw rejected("仅支持JPEG、PNG、WebP图片或PDF");
 }
 private static AppBizException rejected(String message){return new AppBizException(AppBizException.RULE_REJECTED,message);}
}
