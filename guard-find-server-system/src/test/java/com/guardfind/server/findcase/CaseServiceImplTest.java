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

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.exception.AppBizException;
import com.guardfind.server.common.enums.GenderEnum;
import com.guardfind.server.findcase.enums.CaseStatusEnum;
import com.guardfind.server.findcase.enums.UrgencyLevelEnum;
import com.guardfind.server.findcase.mapper.CaseStatusLogMapper;
import com.guardfind.server.findcase.mapper.CaseVersionMapper;
import com.guardfind.server.findcase.mapper.FavoriteMapper;
import com.guardfind.server.findcase.mapper.FindCaseAliasMapper;
import com.guardfind.server.findcase.mapper.FindCaseMapper;
import com.guardfind.server.findcase.mapper.FindCaseMediaMapper;
import com.guardfind.server.findcase.mapper.UserTrackMapper;
import com.guardfind.server.findcase.model.entity.CaseStatusLogDO;
import com.guardfind.server.findcase.model.entity.CaseVersionDO;
import com.guardfind.server.findcase.model.entity.FindCaseDO;
import com.guardfind.server.findcase.model.entity.FindCaseMediaDO;
import com.guardfind.server.findcase.model.req.CaseSubmitReq;
import com.guardfind.server.findcase.model.resp.CasePublicResp;
import com.guardfind.server.findcase.service.ContentCheckGateway;
import com.guardfind.server.findcase.service.impl.CaseServiceImpl;
import com.guardfind.server.findcase.service.impl.CaseStatusServiceImpl;
import com.guardfind.server.findcase.support.CaseViewAssembler;
import com.guardfind.server.notification.service.AppMessageService;
import com.guardfind.server.support.ContainerlessTestBase;
import com.guardfind.server.system.service.FileService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CaseServiceImplTest extends ContainerlessTestBase {

    private final FindCaseMapper caseMapper = mock(FindCaseMapper.class);
    private final FindCaseMediaMapper mediaMapper = mock(FindCaseMediaMapper.class);
    private final CaseVersionMapper versionMapper = mock(CaseVersionMapper.class);
    private final CaseStatusLogMapper statusLogMapper = mock(CaseStatusLogMapper.class);
    private final CaseViewAssembler assembler = mock(CaseViewAssembler.class);
    private final AppMessageService messages = mock(AppMessageService.class);
    private final UserTrackMapper tracks = mock(UserTrackMapper.class);
    private CaseServiceImpl service;

    @BeforeEach
    void setUp() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, FindCaseDO.class);
        TableInfoHelper.initTableInfo(assistant, FindCaseMediaDO.class);
        var statuses = new CaseStatusServiceImpl(caseMapper, statusLogMapper, mock(FavoriteMapper.class), messages);
        service = new CaseServiceImpl(caseMapper, mediaMapper, mock(FindCaseAliasMapper.class), versionMapper, tracks, mock(FileService.class), assembler, mock(ContentCheckGateway.class), statuses, messages);
    }

    @Test
    void nonexistentPublicCaseReturns404() {
        assertUnavailable(() -> service.getPublic(100L));
        verifyNoInteractions(assembler, tracks);
        verify(caseMapper, never()).lambdaUpdate();
    }

    @ParameterizedTest
    @EnumSource(value = CaseStatusEnum.class, names = {"PUBLISHED", "FOUND"}, mode = EnumSource.Mode.EXCLUDE)
    void hiddenPublicStatusesReturnSame404(CaseStatusEnum status) {
        FindCaseDO item = validCase();
        item.setStatus(status);
        when(caseMapper.selectById(100L)).thenReturn(item);
        assertUnavailable(() -> service.getPublic(100L));
        verifyNoInteractions(assembler, tracks);
        verify(caseMapper, never()).lambdaUpdate();
    }

    @ParameterizedTest
    @EnumSource(value = CaseStatusEnum.class, names = {"PUBLISHED", "FOUND"})
    @SuppressWarnings("unchecked")
    void publishedAndFoundRemainVisible(CaseStatusEnum status) {
        FindCaseDO item = validCase();
        item.setStatus(status);
        when(caseMapper.selectById(100L)).thenReturn(item);
        var update = new LambdaUpdateChainWrapper<>(caseMapper);
        when(caseMapper.lambdaUpdate()).thenReturn(update);
        var response = new CasePublicResp();
        when(assembler.toPublic(item)).thenReturn(response);
        assertThat(service.getPublic(100L)).isSameAs(response);
        verify(caseMapper).update(any(), any());
    }

    @Test
    void erasedAndMissingFullCaseReturnSame404() {
        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            assertUnavailable(() -> service.getFull(100L));
            FindCaseDO item = validCase();
            item.setStatus(CaseStatusEnum.ERASED);
            when(caseMapper.selectById(100L)).thenReturn(item);
            assertUnavailable(() -> service.getFull(100L));
            verifyNoInteractions(assembler, tracks);
        }
    }

    @Test
    void hiddenCaseHttpResponseKeepsEnvelopeStatus200() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
            .standaloneSetup(new com.guardfind.server.findcase.controller.CaseController(service))
            .setControllerAdvice(new com.guardfind.server.app.exception.AppExceptionHandler())
            .build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/app/find/case/100"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                .value("404"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.success")
                .value(false))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.msg")
                .value("该信息不存在或已下架"));
    }

    @Test
    void duplicateRequiresExplicitForceSubmitBeforeAnyWrite() {
        FindCaseDO item = validCase();
        prepareSubmission(item);
        var duplicate = new FindCaseDO();
        duplicate.setCaseNo("GF-EXISTING");
        when(caseMapper.selectList(any())).thenReturn(List.of(duplicate));
        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            assertThatThrownBy(() -> service.submit(100L, new CaseSubmitReq()))
                .isInstanceOfSatisfying(AppBizException.class, e -> assertThat(e.getCode()).isEqualTo("4005"))
                .hasMessageContaining("GF-EXISTING");
            verify(caseMapper, never()).updateById(any(FindCaseDO.class));
            verifyNoInteractions(versionMapper, statusLogMapper, messages, assembler);
        }
    }

    @Test
    void forcedSubmissionWritesIncreasingMaskedVersionsAndStatusLogs() {
        FindCaseDO item = validCase();
        prepareSubmission(item);
        var req = new CaseSubmitReq();
        req.setForceSubmit(true);
        try (var context = mockStatic(AppUserContextHolder.class)) {
            context.when(AppUserContextHolder::getUserIdRequired).thenReturn(7L);
            service.submit(100L, req);
            item.setStatus(CaseStatusEnum.REJECTED);
            service.submit(100L, req);
        }
        assertThat(item.getVersion()).isEqualTo(2);
        assertThat(item.getStatus()).isEqualTo(CaseStatusEnum.PENDING_REVIEW);
        assertThat(item.getUrgencyLevel()).isEqualTo(UrgencyLevelEnum.URGENT);
        verify(caseMapper, never()).lambdaQuery();
        var versions = ArgumentCaptor.forClass(CaseVersionDO.class);
        verify(versionMapper, org.mockito.Mockito.times(2)).insert(versions.capture());
        assertThat(versions.getAllValues()).extracting(CaseVersionDO::getSeq).containsExactly(1, 2);
        for (CaseVersionDO version : versions.getAllValues()) {
            assertThat(version.getCaseId()).isEqualTo(100L);
            assertThat(JSONUtil.parseObj(version.getSnapshotJson()).getStr("contactPhone")).isEqualTo("138****8000");
            assertThat(JSONUtil.parseObj(version.getSnapshotJson()).getStr("status")).isEqualTo("PENDING_REVIEW");
        }
        var logs = ArgumentCaptor.forClass(CaseStatusLogDO.class);
        verify(statusLogMapper, org.mockito.Mockito.times(2)).insert(logs.capture());
        assertThat(logs.getAllValues()).extracting(CaseStatusLogDO::getFromStatus)
            .containsExactly(CaseStatusEnum.DRAFT, CaseStatusEnum.REJECTED);
        assertThat(logs.getAllValues()).extracting(CaseStatusLogDO::getToStatus)
            .containsOnly(CaseStatusEnum.PENDING_REVIEW);
    }

    private void prepareSubmission(FindCaseDO item) {
        when(caseMapper.selectById(100L)).thenReturn(item);
        when(caseMapper.selectCount(any())).thenReturn(0L);
        when(mediaMapper.lambdaQuery()).thenReturn(new LambdaQueryChainWrapper<>(mediaMapper));
        when(mediaMapper.selectList(any())).thenReturn(List.of());
        when(caseMapper.lambdaQuery()).thenReturn(new LambdaQueryChainWrapper<>(caseMapper));
        when(caseMapper.selectList(any())).thenReturn(List.of());
    }

    private static FindCaseDO validCase() {
        FindCaseDO item = new FindCaseDO();
        item.setId(100L);
        item.setOwnerId(7L);
        item.setName("测试档案");
        item.setGender(GenderEnum.MALE);
        item.setStatus(CaseStatusEnum.DRAFT);
        item.setVersion(0);
        item.setMissingTime(LocalDateTime.now().minusHours(1));
        item.setProvinceCode("130000");
        item.setCityCode("130400");
        item.setDistrictCode("130431");
        item.setCaseType("MISSING");
        item.setFeatures("左眉有疤痕");
        item.setAgreementAccepted(true);
        item.setAgreementVersion("v1");
        item.setContactPhone("13800138000");
        return item;
    }

    private static void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(AppBizException.class, e -> assertThat(e.getCode())
            .isEqualTo("404")).hasMessage("该信息不存在或已下架");
    }
}
