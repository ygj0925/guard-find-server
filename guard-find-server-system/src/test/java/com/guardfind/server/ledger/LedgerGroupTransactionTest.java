/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.guardfind.server.ledger;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.guardfind.server.app.context.AppUserContext;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.ledger.mapper.*;
import com.guardfind.server.ledger.model.entity.*;
import com.guardfind.server.ledger.model.req.*;
import com.guardfind.server.ledger.service.impl.LedgerGroupServiceImpl;
import com.guardfind.server.support.ContainerlessTestBase;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class LedgerGroupTransactionTest extends ContainerlessTestBase {
    private JdbcTemplate jdbc;
    private LedgerGroupMapper groups;
    private LedgerGroupMemberMapper members;
    private LedgerGroupServiceImpl service;

    @BeforeEach
    void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:group" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE groups_test(id BIGINT PRIMARY KEY, code VARCHAR(20))");
        jdbc.execute("CREATE TABLE members_test(group_id BIGINT, user_id BIGINT, UNIQUE(group_id,user_id))");
        groups = mock(LedgerGroupMapper.class); members = mock(LedgerGroupMemberMapper.class);
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, LedgerGroupDO.class);
        TableInfoHelper.initTableInfo(assistant, LedgerGroupMemberDO.class);
        when(groups.insert(any(LedgerGroupDO.class))).thenAnswer(inv -> {
            var group = (LedgerGroupDO)inv.getArgument(0); group.setId(5L);
            jdbc.update("INSERT INTO groups_test VALUES (5, ?)", group.getInviteCode()); return 1;
        });
        when(groups.selectCount(any())).thenReturn(0L);
        when(groups.selectOne(any())).thenAnswer(inv -> {
            var query = (LambdaQueryWrapper<?>)inv.getArgument(0);
            assertThat(query.getSqlSegment()).contains("FOR UPDATE");
            return jdbc.query("SELECT id, code FROM groups_test WHERE code = 'ABCDEF' FOR UPDATE", (rs, row) -> {
                var group = new LedgerGroupDO(); group.setId(rs.getLong(1)); group.setInviteCode(rs.getString(2));
                group.setOwnerUserId(100L); return group;
            }).stream().findFirst().orElse(null);
        });
        when(members.selectOne(any())).thenAnswer(inv -> jdbc.query("SELECT user_id FROM members_test WHERE group_id = 5 AND user_id = ?",
            (rs, row) -> { var m = new LedgerGroupMemberDO(); m.setUserId(rs.getLong(1)); m.setRole(2); return m; },
            AppUserContextHolder.getUserIdRequired()).stream().findFirst().orElse(null));
        when(members.selectCount(any())).thenAnswer(inv -> jdbc.queryForObject("SELECT COUNT(*) FROM members_test WHERE group_id = 5", Long.class));
        when(members.insert(any(LedgerGroupMemberDO.class))).thenAnswer(inv -> {
            var member = (LedgerGroupMemberDO)inv.getArgument(0);
            return jdbc.update("INSERT INTO members_test VALUES (?, ?)", member.getGroupId(), member.getUserId());
        });
        var target = new LedgerGroupServiceImpl(groups, members, mock(LedgerRecordMapper.class), mock(AppUserMapper.class));
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
        service = (LedgerGroupServiceImpl)proxy.getProxy();
    }

    @AfterEach
    void closeDatabase() { jdbc.execute("SHUTDOWN"); AppUserContextHolder.clear(); }

    @Test
    void failedOwnerInsertRollsBackTheGroup() {
        user(7L);
        doThrow(new IllegalStateException("insert failed")).when(members).insert(any(LedgerGroupMemberDO.class));
        var request = new LedgerGroupReq(); request.setName("family");
        assertThatThrownBy(() -> service.create(request)).hasMessage("insert failed");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM groups_test", Long.class)).isZero();
    }

    @Test
    void concurrentJoinCannotExceedTwelveAndRetryIsIdempotent() throws Exception {
        jdbc.update("INSERT INTO groups_test VALUES (5, 'ABCDEF')");
        for (int i = 100; i < 111; i++) jdbc.update("INSERT INTO members_test VALUES (5, ?)", i);
        var executor = Executors.newFixedThreadPool(2);
        var start = new CyclicBarrier(2);
        try {
            var first = executor.submit(() -> join(7L, start));
            var second = executor.submit(() -> join(8L, start));
            boolean a = first.get(15, TimeUnit.SECONDS), b = second.get(15, TimeUnit.SECONDS);
            assertThat(a ^ b).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM members_test", Long.class)).isEqualTo(12L);
            user(a ? 7L : 8L);
            var req = new LedgerJoinReq(); req.setInviteCode("ABCDEF");
            service.join(req);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM members_test", Long.class)).isEqualTo(12L);
        } finally { executor.shutdownNow(); }
    }

    private boolean join(long userId, CyclicBarrier start) throws Exception {
        user(userId);
        try {
            start.await(5, TimeUnit.SECONDS);
            var req = new LedgerJoinReq(); req.setInviteCode("ABCDEF");
            try { service.join(req); return true; }
            catch (top.continew.starter.core.exception.BusinessException e) {
                if (!e.getMessage().contains("成员已满")) throw e;
                return false;
            }
        } finally { AppUserContextHolder.clear(); }
    }

    @SuppressWarnings("unchecked")
    private void user(long id) {
        var context = new AppUserContext(); context.setId(id);
        ((ThreadLocal<AppUserContext>)ReflectionTestUtils.getField(AppUserContextHolder.class, "CONTEXT_HOLDER")).set(context);
    }
}
