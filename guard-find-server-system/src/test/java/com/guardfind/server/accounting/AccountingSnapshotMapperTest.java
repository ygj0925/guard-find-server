package com.guardfind.server.accounting;
import com.guardfind.server.accounting.mapper.AccountingSnapshotMapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccountingSnapshotMapperTest {
    @Test void xmlEnforcesOwnerAndCompareAndSwap() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:accounting;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE gf_accounting_snapshot (user_id BIGINT PRIMARY KEY, version BIGINT NOT NULL, payload CLOB NOT NULL, operation_id VARCHAR(64) NOT NULL, update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            statement.execute("CREATE TABLE gf_app_user (id BIGINT PRIMARY KEY,account_status INT)");
            statement.execute("INSERT INTO gf_app_user VALUES (7,3),(8,4)");
        }
        var config = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(AccountingSnapshotMapper.class);
        String resource = "mapper/accounting/AccountingSnapshotMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        try (var session = new SqlSessionFactoryBuilder().build(config).openSession(true)) {
            var mapper = session.getMapper(AccountingSnapshotMapper.class);
            var snapshot = new AccountingSnapshot(); snapshot.setUserId(7L); snapshot.setVersion(1L); snapshot.setOperationId("operation-1"); snapshot.setPayload("{}");
            assertEquals(1, mapper.insertSnapshot(snapshot)); assertNull(mapper.find(8L)); assertEquals(1L, mapper.find(7L).getVersion());
            snapshot.setVersion(2L); snapshot.setPayload("{\"changed\":true}");
            assertEquals(0, mapper.replace(snapshot, 0));
            assertEquals(1, mapper.replace(snapshot, 1));
            assertEquals(0, mapper.replace(snapshot, 1));
            assertEquals("{\"changed\":true}", mapper.find(7L).getPayload());
            snapshot.setUserId(8L);mapper.insertSnapshot(snapshot);
            assertEquals(1,mapper.deleteCancelledUsers());assertNull(mapper.find(8L));assertNotNull(mapper.find(7L));
        }
    }
}
