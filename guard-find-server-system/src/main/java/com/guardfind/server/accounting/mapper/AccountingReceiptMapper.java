package com.guardfind.server.accounting.mapper;
import com.guardfind.server.accounting.AccountingReceipt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface AccountingReceiptMapper {
    int insertReceipt(AccountingReceipt receipt);
    AccountingReceipt find(@Param("id") String id,@Param("userId") Long userId);
    int remove(@Param("id") String id,@Param("userId") Long userId);
    long bytesForUser(@Param("userId") Long userId);
    java.util.List<AccountingReceipt> list(@Param("userId") Long userId);
    int deleteCancelledUsers();
}
