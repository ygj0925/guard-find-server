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

package com.guardfind.server.moderation;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.findcase.enums.CaseStatusEnum;
import com.guardfind.server.findcase.mapper.FindCaseMapper;
import com.guardfind.server.findcase.model.entity.FindCaseDO;
import com.guardfind.server.lead.mapper.LeadMapper;
import com.guardfind.server.lead.model.entity.LeadDO;
import com.guardfind.server.moderation.constant.ModerationConstants;
import com.guardfind.server.moderation.enums.ReportStatusEnum;
import com.guardfind.server.moderation.enums.ReportTargetTypeEnum;
import com.guardfind.server.moderation.mapper.ReportMapper;
import com.guardfind.server.moderation.model.entity.ReportDO;
import com.guardfind.server.moderation.model.req.ReportReq;
import com.guardfind.server.moderation.model.resp.ReportResp;
import com.guardfind.server.moderation.service.impl.ReportServiceImpl;
import com.guardfind.server.support.ContainerlessTestBase;
import com.guardfind.server.system.service.DictItemService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import top.continew.starter.core.exception.BadRequestException;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.model.resp.LabelValueResp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 使用真实查询条件验证编排，不验证数据库并发或事务。 */
@DisplayName("重复举报合并")
class ReportServiceImplTest extends ContainerlessTestBase {

    private static final Long REPORTER_ID = 7L;
    private static final Long CASE_ID = 101L;
    private static final Long EXISTING_REPORT_ID = 501L;
    private static final Long NEW_REPORT_ID = 502L;
    private static final LocalDateTime ORIGINAL_TIME = LocalDateTime.of(2026, 9, 24, 10, 0);

    private ReportMapper reportMapper;
    private FindCaseMapper findCaseMapper;
    private LeadMapper leadMapper;
    private AppUserMapper appUserMapper;
    private DictItemService dictItemService;
    private ReportServiceImpl service;
    private MockedStatic<AppUserContextHolder> userContext;

    @BeforeAll
    static void initTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "report-service-test");
        assistant.setCurrentNamespace(ReportServiceImplTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, ReportDO.class);
        TableInfoHelper.initTableInfo(assistant, FindCaseDO.class);
    }

    @BeforeEach
    void setUp() {
        reportMapper = mock(ReportMapper.class);
        findCaseMapper = mock(FindCaseMapper.class);
        leadMapper = mock(LeadMapper.class);
        appUserMapper = mock(AppUserMapper.class);
        dictItemService = mock(DictItemService.class);
        service = new ReportServiceImpl(reportMapper, findCaseMapper, leadMapper, appUserMapper, dictItemService);

        // 每次调用创建独立的真实链，避免 wrapper 条件跨请求累积。
        when(reportMapper.lambdaQuery()).thenAnswer(invocation -> new LambdaQueryChainWrapper<>(reportMapper));
        when(findCaseMapper.lambdaUpdate()).thenAnswer(invocation -> new LambdaUpdateChainWrapper<>(findCaseMapper));
        when(reportMapper.selectList(any())).thenReturn(List.of());
        when(reportMapper.updateById(any(ReportDO.class))).thenReturn(1);
        when(findCaseMapper.update(isNull(), any())).thenReturn(1);
        doAnswer(invocation -> {
            ReportDO report = invocation.getArgument(0);
            report.setId(NEW_REPORT_ID);
            return 1;
        }).when(reportMapper).insert(any(ReportDO.class));
        when(findCaseMapper.selectById(CASE_ID)).thenReturn(publishedCase(CASE_ID));
        when(dictItemService.listByDictCode(ModerationConstants.DICT_REPORT_REASON)).thenReturn(List
            .of(new LabelValueResp("诈骗", "FRAUD"), new LabelValueResp("虚假信息", "FALSE_INFO")));

        userContext = mockStatic(AppUserContextHolder.class);
        userContext.when(AppUserContextHolder::getUserIdRequired).thenReturn(REPORTER_ID);
    }

    @AfterEach
    void closeUserContext() {
        if (userContext != null) {
            userContext.close();
        }
    }

    @Test
    @DisplayName("首次举报：按同人同目标查询，insert 一条并规范化理由、描述、证据")
    void firstReportInsertsNormalizedRecordWithExactIdentityFilter() {
        ReportReq req = request(ReportTargetTypeEnum.CASE, CASE_ID, "  fraud  ", "  首次补充  ");
        List<String> expectedMedia = IntStream.range(0, ModerationConstants.MAX_REPORT_MEDIA_COUNT)
            .mapToObj(index -> "https://example.test/evidence-" + index + ".jpg")
            .toList();
        // 原始列表超过上限，但去空、trim、去重后恰好达到上限。
        req.setMediaUrls(new ArrayList<>(Arrays.asList(null, "", " \t ", " " + expectedMedia.get(0) + " ")));
        req.getMediaUrls().addAll(expectedMedia);

        ReportResp resp = service.report(req);

        assertIdentityFilter(captureQuery(), REPORTER_ID, ReportTargetTypeEnum.CASE, CASE_ID);
        ReportDO inserted = captureInsert();
        assertEquals(NEW_REPORT_ID, inserted.getId());
        assertEquals(REPORTER_ID, inserted.getReporterId());
        assertEquals(ReportTargetTypeEnum.CASE, inserted.getTargetType());
        assertEquals(CASE_ID, inserted.getTargetId());
        assertEquals("FRAUD", inserted.getReasonType());
        assertEquals("首次补充", inserted.getDescription());
        assertEquals(expectedMedia, inserted.getMediaUrls());
        assertEquals(ReportStatusEnum.PENDING, inserted.getStatus());
        assertEquals(1, inserted.getHitCount());
        assertEquals(REPORTER_ID, inserted.getCreateUser());
        assertEquals(REPORTER_ID, inserted.getUpdateUser());
        assertNotNull(inserted.getCreateTime());
        assertEquals(inserted.getCreateTime(), inserted.getUpdateTime());
        assertEquals(NEW_REPORT_ID, resp.getId());
        assertEquals(1, resp.getHitCount());
        assertEquals("张三", resp.getTargetName());
        assertEquals(expectedMedia, resp.getMediaUrls());
        verify(dictItemService).listByDictCode(ModerationConstants.DICT_REPORT_REASON);
        verify(reportMapper, never()).updateById(any(ReportDO.class));
        assertNoQueueUpdate();
    }

    @Test
    @DisplayName("首次举报：字典缺失按兜底理由放行，空描述及未传证据规范化")
    void firstReportUsesFallbackReasonsAndEmptyMedia() {
        when(dictItemService.listByDictCode(ModerationConstants.DICT_REPORT_REASON)).thenReturn(List.of());

        ReportResp resp = service.report(request(ReportTargetTypeEnum.CASE, CASE_ID, " other ", " \t "));

        ReportDO inserted = captureInsert();
        assertEquals("OTHER", inserted.getReasonType());
        assertNull(inserted.getDescription());
        assertEquals(List.of(), inserted.getMediaUrls());
        assertEquals(List.of(), resp.getMediaUrls());
        assertEquals(1, resp.getHitCount());
        verify(reportMapper, never()).updateById(any(ReportDO.class));
        assertNoQueueUpdate();
    }

    @Test
    @DisplayName("首次举报：规范化后证据超过上限，不 insert 或 update")
    void tooManyDistinctMediaUrlsRejectsWithoutWrites() {
        ReportReq req = request(ReportTargetTypeEnum.CASE, CASE_ID, "FRAUD", null);
        req.setMediaUrls(IntStream.rangeClosed(0, ModerationConstants.MAX_REPORT_MEDIA_COUNT)
            .mapToObj(index -> "https://example.test/" + index + ".jpg")
            .toList());

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.report(req));

        assertTrue(ex.getMessage().contains("证据图片最多上传"));
        assertIdentityFilter(captureQuery(), REPORTER_ID, ReportTargetTypeEnum.CASE, CASE_ID);
        assertNoWrites();
    }

    @Test
    @DisplayName("重复举报：不 insert，保留原 ID/理由，仅 hitCount + 1 并采用新非空描述")
    void duplicateUpdatesExistingIdAndKeepsOriginalReason() {
        ReportDO existing = existingReport(REPORTER_ID, ReportTargetTypeEnum.CASE, CASE_ID, 1);
        stubReportRows(existing);

        ReportResp resp = service.report(request(ReportTargetTypeEnum.CASE, CASE_ID, "FALSE_INFO", "  新补充  "));

        assertIdentityFilter(captureQuery(), REPORTER_ID, ReportTargetTypeEnum.CASE, CASE_ID);
        ReportDO update = captureUpdate();
        assertEquals(EXISTING_REPORT_ID, update.getId());
        assertEquals(2, update.getHitCount());
        assertEquals("新补充", update.getDescription());
        assertEquals(REPORTER_ID, update.getUpdateUser());
        assertNotNull(update.getUpdateTime());
        assertNull(update.getReasonType(), "局部更新不能覆盖首次理由");
        assertNull(update.getReporterId());
        assertNull(update.getTargetType());
        assertNull(update.getTargetId());
        assertNull(update.getStatus());
        assertNull(update.getMediaUrls());
        assertNull(update.getCreateTime());
        assertNull(update.getCreateUser());
        assertEquals(EXISTING_REPORT_ID, resp.getId());
        assertEquals("FRAUD", resp.getReasonType());
        assertEquals(2, resp.getHitCount());
        assertEquals("新补充", resp.getDescription());
        assertEquals(existing.getMediaUrls(), resp.getMediaUrls());
        assertEquals(ORIGINAL_TIME, resp.getCreateTime());
        assertEquals(ReportStatusEnum.PENDING, resp.getStatus());
        verify(reportMapper, never()).insert(any(ReportDO.class));
        assertNoQueueUpdate();
    }

    @Test
    @DisplayName("重复举报：未传补充描述时保留旧描述")
    void duplicateWithNullDescriptionKeepsOldDescription() {
        stubReportRows(existingReport(REPORTER_ID, ReportTargetTypeEnum.CASE, CASE_ID, 1));

        ReportResp resp = service.report(request(ReportTargetTypeEnum.CASE, CASE_ID, "FALSE_INFO", null));

        ReportDO update = captureUpdate();
        assertEquals(EXISTING_REPORT_ID, update.getId());
        assertEquals(2, update.getHitCount());
        assertEquals("旧补充", update.getDescription());
        assertEquals("旧补充", resp.getDescription());
        assertEquals("FRAUD", resp.getReasonType());
        verify(reportMapper, never()).insert(any(ReportDO.class));
        assertNoQueueUpdate();
    }

    @Test
    @DisplayName("重复举报：空字符串或纯空白补充均保留旧描述")
    void duplicateWithBlankDescriptionKeepsOldDescription() {
        // 两种输入分别从 hitCount = 1 的独立记录开始。
        for (String description : List.of("", " \t\n ")) {
            stubReportRows(existingReport(REPORTER_ID, ReportTargetTypeEnum.CASE, CASE_ID, 1));

            ReportResp resp = service.report(request(ReportTargetTypeEnum.CASE, CASE_ID, "FALSE_INFO", description));

            assertEquals(2, resp.getHitCount());
            assertEquals("旧补充", resp.getDescription());
            assertEquals("FRAUD", resp.getReasonType());
        }
        ArgumentCaptor<ReportDO> updates = ArgumentCaptor.forClass(ReportDO.class);
        verify(reportMapper, times(2)).updateById(updates.capture());
        for (ReportDO update : updates.getAllValues()) {
            assertEquals(EXISTING_REPORT_ID, update.getId());
            assertEquals(2, update.getHitCount());
            assertEquals("旧补充", update.getDescription());
            assertNull(update.getReasonType());
        }
        verify(reportMapper, never()).insert(any(ReportDO.class));
        assertNoQueueUpdate();
    }

    @Test
    @DisplayName("重复举报：历史 hitCount 为空时按一次起算，不新增记录")
    void duplicateWithNullHitCountStartsFromOne() {
        stubReportRows(existingReport(REPORTER_ID, ReportTargetTypeEnum.CASE, CASE_ID, null));

        ReportResp resp = service.report(request(ReportTargetTypeEnum.CASE, CASE_ID, "FRAUD", null));

        ReportDO update = captureUpdate();
        assertEquals(EXISTING_REPORT_ID, update.getId());
        assertEquals(2, update.getHitCount());
        assertEquals(2, resp.getHitCount());
        verify(reportMapper, never()).insert(any(ReportDO.class));
        assertNoQueueUpdate();
    }

    @Test
    @DisplayName("同人同类型但不同目标 ID：新建，不能误合并已有举报")
    void differentTargetIdDoesNotMerge() {
        ReportDO existing = existingReport(REPORTER_ID, ReportTargetTypeEnum.CASE, CASE_ID, 1);
        stubReportRows(existing);
        Long otherCaseId = CASE_ID + 1;
        when(findCaseMapper.selectById(otherCaseId)).thenReturn(publishedCase(otherCaseId));

        ReportResp resp = service.report(request(ReportTargetTypeEnum.CASE, otherCaseId, "FALSE_INFO", null));

        assertIdentityFilter(captureQuery(), REPORTER_ID, ReportTargetTypeEnum.CASE, otherCaseId);
        assertNewReport(resp, REPORTER_ID, ReportTargetTypeEnum.CASE, otherCaseId);
        assertEquals(1, existing.getHitCount());
        assertEquals("FRAUD", existing.getReasonType());
    }

    @Test
    @DisplayName("同人同目标数字 ID 但类型不同：CASE 与 LEAD 不误合并")
    void differentTargetTypeWithSameIdDoesNotMerge() {
        ReportDO existing = existingReport(REPORTER_ID, ReportTargetTypeEnum.CASE, CASE_ID, 1);
        stubReportRows(existing);
        LeadDO lead = new LeadDO();
        lead.setId(CASE_ID);
        lead.setCaseId(202L);
        lead.setDescription("线索摘要");
        when(leadMapper.selectById(CASE_ID)).thenReturn(lead);

        ReportResp resp = service.report(request(ReportTargetTypeEnum.LEAD, CASE_ID, "FALSE_INFO", null));

        assertIdentityFilter(captureQuery(), REPORTER_ID, ReportTargetTypeEnum.LEAD, CASE_ID);
        assertNewReport(resp, REPORTER_ID, ReportTargetTypeEnum.LEAD, CASE_ID);
        assertEquals("线索摘要", resp.getTargetName());
        assertEquals(1, existing.getHitCount());
    }

    @Test
    @DisplayName("不同举报人对同一目标：分别新建，不合并他人记录")
    void differentReporterDoesNotMerge() {
        ReportDO existing = existingReport(REPORTER_ID, ReportTargetTypeEnum.CASE, CASE_ID, 1);
        stubReportRows(existing);
        Long anotherReporter = REPORTER_ID + 1;
        userContext.when(AppUserContextHolder::getUserIdRequired).thenReturn(anotherReporter);

        ReportResp resp = service.report(request(ReportTargetTypeEnum.CASE, CASE_ID, "FALSE_INFO", null));

        assertIdentityFilter(captureQuery(), anotherReporter, ReportTargetTypeEnum.CASE, CASE_ID);
        assertNewReport(resp, anotherReporter, ReportTargetTypeEnum.CASE, CASE_ID);
        assertEquals(1, existing.getHitCount());
        assertEquals(REPORTER_ID, existing.getReporterId());
    }

    @Test
    @DisplayName("字典已配置：不在字典的理由即使属于兜底集也拒绝，不查询目标或写入")
    void reasonOutsideConfiguredDictionaryRejectsBeforeTargetLookup() {
        when(dictItemService.listByDictCode(ModerationConstants.DICT_REPORT_REASON)).thenReturn(List
            .of(new LabelValueResp("诈骗", "FRAUD")));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service
            .report(request(ReportTargetTypeEnum.CASE, CASE_ID, "FALSE_INFO", null)));

        assertTrue(ex.getMessage().contains("举报理由不在可选范围内"));
        verifyNoInteractions(reportMapper, findCaseMapper, leadMapper, appUserMapper);
    }

    @Test
    @DisplayName("字典缺失：任意未知理由不放行，不查询目标或写入")
    void unknownFallbackReasonRejectsWithoutWrites() {
        when(dictItemService.listByDictCode(ModerationConstants.DICT_REPORT_REASON)).thenReturn(List.of());

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service
            .report(request(ReportTargetTypeEnum.CASE, CASE_ID, "NOT_A_REASON", null)));

        assertTrue(ex.getMessage().contains("举报理由不在可选范围内"));
        verifyNoInteractions(reportMapper, findCaseMapper, leadMapper, appUserMapper);
    }

    @Test
    @DisplayName("理由为空或空白：校验失败，不查询字典、目标或写入")
    void blankReasonRejectsWithoutWrites() {
        for (String reason : Arrays.asList(null, "", " \t ")) {
            BadRequestException ex = assertThrows(BadRequestException.class, () -> service
                .report(request(ReportTargetTypeEnum.CASE, CASE_ID, reason, null)));
            assertTrue(ex.getMessage().contains("举报理由不能为空"));
        }
        verifyNoInteractions(dictItemService, reportMapper, findCaseMapper, leadMapper, appUserMapper);
    }

    @Test
    @DisplayName("CASE/LEAD/USER 目标不存在：不查询既有举报，不 insert 或 update")
    void missingTargetsRejectWithoutWrites() {
        Long missingId = 999L;
        for (ReportTargetTypeEnum type : ReportTargetTypeEnum.values()) {
            BusinessException ex = assertThrows(BusinessException.class, () -> service
                .report(request(type, missingId, "FRAUD", null)));
            assertTrue(ex.getMessage().contains("举报对象不存在或已删除"));
        }
        verify(findCaseMapper).selectById(missingId);
        verify(leadMapper).selectById(missingId);
        verify(appUserMapper).selectById(missingId);
        verifyNoInteractions(reportMapper);
        assertNoWrites();
    }

    @Test
    @DisplayName("目标档案已 ERASED：视为不存在，不合并或新增举报")
    void erasedCaseRejectsWithoutWrites() {
        FindCaseDO erased = publishedCase(CASE_ID);
        erased.setStatus(CaseStatusEnum.ERASED);
        when(findCaseMapper.selectById(CASE_ID)).thenReturn(erased);

        BusinessException ex = assertThrows(BusinessException.class, () -> service
            .report(request(ReportTargetTypeEnum.CASE, CASE_ID, "FRAUD", null)));

        assertTrue(ex.getMessage().contains("举报对象不存在或已删除"));
        verifyNoInteractions(reportMapper);
        assertNoWrites();
    }

    @Test
    @DisplayName("顺序重复举报：仅恰好达到阈值时抬升 report_count，超过阈值不再触发")
    void sequentialDuplicatesSurfaceCaseExactlyOnceAtThreshold() {
        stubReportRows(existingReport(REPORTER_ID, ReportTargetTypeEnum.CASE, CASE_ID, 1));
        int threshold = ModerationConstants.REPORT_HIT_THRESHOLD;
        int finalHitCount = threshold + 2;

        for (int hits = 2; hits <= finalHitCount; hits++) {
            ReportResp resp = service.report(request(ReportTargetTypeEnum.CASE, CASE_ID, "FRAUD", null));
            assertEquals(EXISTING_REPORT_ID, resp.getId());
            assertEquals(hits, resp.getHitCount());
            int expectedQueueUpdates = hits < threshold ? 0 : 1;
            verify(findCaseMapper, times(expectedQueueUpdates)).lambdaUpdate();
            verify(findCaseMapper, times(expectedQueueUpdates)).update(isNull(), any());
        }

        ArgumentCaptor<ReportDO> updates = ArgumentCaptor.forClass(ReportDO.class);
        verify(reportMapper, times(finalHitCount - 1)).updateById(updates.capture());
        assertEquals(IntStream.rangeClosed(2, finalHitCount).boxed().toList(), updates.getAllValues()
            .stream()
            .map(ReportDO::getHitCount)
            .toList());
        assertTrue(updates.getAllValues().stream().allMatch(update -> EXISTING_REPORT_ID.equals(update.getId())));
        verify(reportMapper, never()).insert(any(ReportDO.class));
        assertQueueUpdateFor(CASE_ID);
    }

    @Test
    @DisplayName("线索举报达到阈值：抬升所属档案而非线索 ID")
    void leadAtThresholdSurfacesItsParentCase() {
        Long leadId = 303L;
        Long parentCaseId = 404L;
        LeadDO lead = new LeadDO();
        lead.setId(leadId);
        lead.setCaseId(parentCaseId);
        lead.setDescription("目标线索");
        when(leadMapper.selectById(leadId)).thenReturn(lead);
        stubReportRows(existingReport(REPORTER_ID, ReportTargetTypeEnum.LEAD, leadId, ModerationConstants.REPORT_HIT_THRESHOLD - 1));

        ReportResp resp = service.report(request(ReportTargetTypeEnum.LEAD, leadId, "FRAUD", null));

        assertEquals(ModerationConstants.REPORT_HIT_THRESHOLD, captureUpdate().getHitCount());
        assertEquals(leadId, resp.getTargetId());
        assertIdentityFilter(captureQuery(), REPORTER_ID, ReportTargetTypeEnum.LEAD, leadId);
        assertQueueUpdateFor(parentCaseId);
        verify(reportMapper, never()).insert(any(ReportDO.class));
    }

    @Test
    @DisplayName("用户举报达到阈值：累加次数但不更新任何档案 report_count")
    void userAtThresholdDoesNotSurfaceAnyCase() {
        Long targetUserId = 606L;
        AppUserDO target = new AppUserDO();
        target.setId(targetUserId);
        target.setNickname("被举报用户");
        when(appUserMapper.selectById(targetUserId)).thenReturn(target);
        stubReportRows(existingReport(REPORTER_ID, ReportTargetTypeEnum.USER, targetUserId, ModerationConstants.REPORT_HIT_THRESHOLD - 1));

        ReportResp resp = service.report(request(ReportTargetTypeEnum.USER, targetUserId, "FRAUD", null));

        assertEquals(ModerationConstants.REPORT_HIT_THRESHOLD, captureUpdate().getHitCount());
        assertEquals(ModerationConstants.REPORT_HIT_THRESHOLD, resp.getHitCount());
        assertEquals("被举报用户", resp.getTargetName());
        assertIdentityFilter(captureQuery(), REPORTER_ID, ReportTargetTypeEnum.USER, targetUserId);
        verify(reportMapper, never()).insert(any(ReportDO.class));
        verifyNoInteractions(findCaseMapper);
    }

    private static ReportReq request(ReportTargetTypeEnum type, Long targetId, String reason, String description) {
        ReportReq req = new ReportReq();
        req.setTargetType(type.name());
        req.setTargetId(targetId);
        req.setReasonType(reason);
        req.setDescription(description);
        return req;
    }

    private static FindCaseDO publishedCase(Long id) {
        FindCaseDO target = new FindCaseDO();
        target.setId(id);
        target.setName("张三");
        target.setStatus(CaseStatusEnum.PUBLISHED);
        return target;
    }

    private static ReportDO existingReport(Long reporterId, ReportTargetTypeEnum type, Long targetId, Integer hits) {
        ReportDO report = new ReportDO();
        report.setId(EXISTING_REPORT_ID);
        report.setReporterId(reporterId);
        report.setTargetType(type);
        report.setTargetId(targetId);
        report.setReasonType("FRAUD");
        report.setDescription("旧补充");
        report.setMediaUrls(List.of("https://example.test/original.jpg"));
        report.setStatus(ReportStatusEnum.PENDING);
        report.setHitCount(hits);
        report.setCreateUser(reporterId);
        report.setCreateTime(ORIGINAL_TIME);
        return report;
    }

    private void stubReportRows(ReportDO... rows) {
        // 仅模拟查询结果选择；依据真实 wrapper 的绑定条件筛选，不代表数据库执行。
        doAnswer(invocation -> {
            LambdaQueryWrapper<ReportDO> query = invocation.getArgument(0);
            Object reporterId = equalityParameter(query, "reporter_id");
            Object targetType = equalityParameter(query, "target_type");
            Object targetId = equalityParameter(query, "target_id");
            return Arrays.stream(rows)
                .filter(row -> row.getReporterId().equals(reporterId))
                .filter(row -> row.getTargetType().equals(targetType))
                .filter(row -> row.getTargetId().equals(targetId))
                .toList();
        }).when(reportMapper).selectList(any());
    }

    private static Object equalityParameter(AbstractWrapper<?, ?, ?> wrapper, String column) {
        // getSqlSegment 触发延迟构建，再从列对应的占位符读取参数，不能只检查 values.contains。
        String sql = wrapper.getSqlSegment();
        Matcher matcher = Pattern.compile("`?\\b" + Pattern
            .quote(column) + "`?\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}").matcher(sql);
        assertTrue(matcher.find(), () -> "缺少等值条件 " + column + "：" + sql);
        return wrapper.getParamNameValuePairs().get(matcher.group(1));
    }

    private static void assertIdentityFilter(LambdaQueryWrapper<ReportDO> query,
                                             Long reporterId,
                                             ReportTargetTypeEnum type,
                                             Long targetId) {
        assertEquals(reporterId, equalityParameter(query, "reporter_id"));
        assertEquals(type, equalityParameter(query, "target_type"));
        assertEquals(targetId, equalityParameter(query, "target_id"));
        assertEquals(3, query.getParamNameValuePairs().size());
        assertTrue(query.getSqlSegment().contains("ORDER BY create_time DESC"));
    }

    private LambdaQueryWrapper<ReportDO> captureQuery() {
        @SuppressWarnings("unchecked") ArgumentCaptor<LambdaQueryWrapper<ReportDO>> query = ArgumentCaptor
            .forClass(LambdaQueryWrapper.class);
        verify(reportMapper).selectList(query.capture());
        return query.getValue();
    }

    private ReportDO captureInsert() {
        ArgumentCaptor<ReportDO> inserted = ArgumentCaptor.forClass(ReportDO.class);
        verify(reportMapper).insert(inserted.capture());
        return inserted.getValue();
    }

    private ReportDO captureUpdate() {
        ArgumentCaptor<ReportDO> update = ArgumentCaptor.forClass(ReportDO.class);
        verify(reportMapper).updateById(update.capture());
        return update.getValue();
    }

    private void assertNewReport(ReportResp resp, Long reporterId, ReportTargetTypeEnum type, Long targetId) {
        ReportDO inserted = captureInsert();
        assertEquals(reporterId, inserted.getReporterId());
        assertEquals(type, inserted.getTargetType());
        assertEquals(targetId, inserted.getTargetId());
        assertEquals("FALSE_INFO", inserted.getReasonType());
        assertEquals(1, inserted.getHitCount());
        assertEquals(NEW_REPORT_ID, resp.getId());
        assertEquals(type, resp.getTargetType());
        assertEquals(targetId, resp.getTargetId());
        assertEquals(1, resp.getHitCount());
        verify(reportMapper, never()).updateById(any(ReportDO.class));
        assertNoQueueUpdate();
    }

    private void assertQueueUpdateFor(Long caseId) {
        @SuppressWarnings("unchecked") ArgumentCaptor<LambdaUpdateWrapper<FindCaseDO>> update = ArgumentCaptor
            .forClass(LambdaUpdateWrapper.class);
        verify(findCaseMapper).update(isNull(), update.capture());
        assertEquals("report_count = report_count + 1", update.getValue().getSqlSet());
        assertEquals(caseId, equalityParameter(update.getValue(), "id"));
        assertEquals(1, update.getValue().getParamNameValuePairs().size());
    }

    private void assertNoQueueUpdate() {
        verify(findCaseMapper, never()).lambdaUpdate();
        verify(findCaseMapper, never()).update(any(), any());
    }

    private void assertNoWrites() {
        verify(reportMapper, never()).insert(any(ReportDO.class));
        verify(reportMapper, never()).updateById(any(ReportDO.class));
        verify(reportMapper, never()).update(any(), any());
        assertNoQueueUpdate();
    }
}
