package com.guardfind.server.accounting.mapper;

import com.guardfind.server.accounting.AccountingSnapshot;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountingSnapshotMapper {
    AccountingSnapshot find(@Param("userId") Long userId);
    int insertSnapshot(AccountingSnapshot snapshot);
    int replace(@Param("snapshot") AccountingSnapshot snapshot, @Param("baseVersion") long baseVersion);
    java.util.List<AccountingSnapshot> pageAfter(@Param("userId") Long userId);
    int deleteCancelledUsers();
}
