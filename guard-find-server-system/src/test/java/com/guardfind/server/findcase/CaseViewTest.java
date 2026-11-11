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

package com.guardfind.server.findcase;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.findcase.converter.CaseViewConverter;
import com.guardfind.server.findcase.enums.CaseOperatorTypeEnum;
import com.guardfind.server.findcase.mapper.CaseStatusLogMapper;
import com.guardfind.server.findcase.mapper.FindCaseAliasMapper;
import com.guardfind.server.findcase.mapper.FindCaseMediaMapper;
import com.guardfind.server.findcase.model.entity.CaseStatusLogDO;
import com.guardfind.server.findcase.model.entity.FindCaseAliasDO;
import com.guardfind.server.findcase.model.entity.FindCaseDO;
import com.guardfind.server.findcase.model.entity.FindCaseMediaDO;
import com.guardfind.server.findcase.model.resp.CaseStatusHistoryResp;
import com.guardfind.server.findcase.support.CaseViewAssembler;
import com.guardfind.server.findcase.support.RegionRepository;
import com.guardfind.server.support.ContainerlessTestBase;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CaseViewTest extends ContainerlessTestBase {

    private final FindCaseMediaMapper mediaMapper = mock(FindCaseMediaMapper.class);
    private final FindCaseAliasMapper aliasMapper = mock(FindCaseAliasMapper.class);
    private final CaseStatusLogMapper statusLogMapper = mock(CaseStatusLogMapper.class);
    private final AppUserMapper appUserMapper = mock(AppUserMapper.class);
    private CaseViewAssembler assembler;

    @BeforeEach
    void setUp() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, FindCaseMediaDO.class);
        TableInfoHelper.initTableInfo(assistant, FindCaseAliasDO.class);
        TableInfoHelper.initTableInfo(assistant, CaseStatusLogDO.class);
        when(mediaMapper.lambdaQuery()).thenReturn(new LambdaQueryChainWrapper<>(mediaMapper));
        when(mediaMapper.selectList(any())).thenReturn(List.of());
        when(aliasMapper.lambdaQuery()).thenReturn(new LambdaQueryChainWrapper<>(aliasMapper));
        when(aliasMapper.selectList(any())).thenReturn(List.of());
        when(statusLogMapper.lambdaQuery()).thenReturn(new LambdaQueryChainWrapper<>(statusLogMapper));
        assembler = new CaseViewAssembler(mediaMapper, aliasMapper, statusLogMapper, appUserMapper, mock(RegionRepository.class));
    }

    @Test
    void assemblerQueriesOnlyDistinctNonNullAppIdsAndDoesNotExposeReviewerNickname() {
        when(statusLogMapper.selectList(any())).thenReturn(List
            .of(log(CaseOperatorTypeEnum.SYS, 7L), log(CaseOperatorTypeEnum.APP, 7L), log(CaseOperatorTypeEnum.SYSTEM, 7L), log(CaseOperatorTypeEnum.SYS, 8L), log(CaseOperatorTypeEnum.APP, 7L), log(CaseOperatorTypeEnum.APP, null)));
        var appUser = new AppUserDO();
        appUser.setId(7L);
        appUser.setNickname("App昵称");
        when(appUserMapper.selectByIds(List.of(7L))).thenReturn(List.of(appUser));

        var response = assembler.toDetail(caseDO());

        assertThat(response.getStatusHistory()).extracting(CaseStatusHistoryResp::getOperator)
            .containsExactly("审核员", "App昵称", "系统", "审核员", "App昵称", CaseOperatorTypeEnum.APP.getDescription());
        verify(appUserMapper).selectByIds(List.of(7L));
        verifyNoMoreInteractions(appUserMapper);
    }

    @ParameterizedTest
    @EnumSource(value = CaseOperatorTypeEnum.class, names = {"SYS", "SYSTEM"})
    void assemblerDoesNotQueryAppUsersForNonAppLogs(CaseOperatorTypeEnum operatorType) {
        when(statusLogMapper.selectList(any())).thenReturn(List.of(log(operatorType, 7L)));

        var response = assembler.toDetail(caseDO());

        assertThat(response.getStatusHistory()).extracting(CaseStatusHistoryResp::getOperator)
            .containsExactly(operatorType.getDescription());
        verifyNoInteractions(appUserMapper);
    }

    @Test
    void converterUsesNicknameOnlyForAppEvenWhenAllTypesShareAnId() {
        var history = CaseViewConverter.toStatusHistory(List
            .of(log(CaseOperatorTypeEnum.SYS, 7L), log(CaseOperatorTypeEnum.APP, 7L), log(CaseOperatorTypeEnum.SYSTEM, 7L)), Map
                .of(7L, "App昵称"));

        assertThat(history).extracting(CaseStatusHistoryResp::getOperator).containsExactly("审核员", "App昵称", "系统");
    }

    @ParameterizedTest
    @EnumSource(value = CaseOperatorTypeEnum.class, names = {"SYS", "SYSTEM"})
    @SuppressWarnings("unchecked")
    void converterDoesNotLookUpNicknameForNonAppLogs(CaseOperatorTypeEnum operatorType) {
        Map<Long, String> operatorNames = mock(Map.class);

        var history = CaseViewConverter.toStatusHistory(List.of(log(operatorType, 7L)), operatorNames);

        assertThat(history).extracting(CaseStatusHistoryResp::getOperator)
            .containsExactly(operatorType.getDescription());
        verifyNoInteractions(operatorNames);
    }

    @Test
    void converterKeepsAppDescriptionFallbackForMissingOrBlankNickname() {
        var history = CaseViewConverter.toStatusHistory(List
            .of(log(CaseOperatorTypeEnum.APP, 7L), log(CaseOperatorTypeEnum.APP, 8L), log(CaseOperatorTypeEnum.APP, null)), Map
                .of(8L, " "));

        assertThat(history).extracting(CaseStatusHistoryResp::getOperator)
            .containsExactly(CaseOperatorTypeEnum.APP.getDescription(), CaseOperatorTypeEnum.APP
                .getDescription(), CaseOperatorTypeEnum.APP.getDescription());
    }

    private static FindCaseDO caseDO() {
        var caseDO = new FindCaseDO();
        caseDO.setId(100L);
        return caseDO;
    }

    private static CaseStatusLogDO log(CaseOperatorTypeEnum operatorType, Long operatorId) {
        var log = new CaseStatusLogDO();
        log.setOperatorType(operatorType.name());
        log.setOperatorId(operatorId);
        return log;
    }
}
