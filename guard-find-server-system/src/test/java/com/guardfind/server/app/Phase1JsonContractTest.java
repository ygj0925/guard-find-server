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

package com.guardfind.server.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardfind.server.app.model.req.AppAccountLoginReq;
import com.guardfind.server.app.model.req.AppLoginReq;
import com.guardfind.server.app.model.req.AppPhoneLoginReq;
import com.guardfind.server.app.model.resp.AppLoginResp;
import com.guardfind.server.app.model.resp.AppUserResp;
import com.guardfind.server.findcase.enums.CaseStatusEnum;
import com.guardfind.server.findcase.enums.UrgencyLevelEnum;
import com.guardfind.server.findcase.model.resp.CaseDetailResp;
import com.guardfind.server.findcase.model.resp.CaseMinePageResp;
import com.guardfind.server.findcase.model.resp.CasePublicResp;
import com.guardfind.server.findcase.model.resp.CaseStatusSummaryResp;
import com.guardfind.server.lead.enums.LeadReplyFromEnum;
import com.guardfind.server.lead.enums.LeadStatusEnum;
import com.guardfind.server.lead.model.resp.LeadPageResp;
import com.guardfind.server.lead.model.resp.LeadReplyResp;
import com.guardfind.server.lead.model.resp.LeadResp;
import com.guardfind.server.moderation.enums.ReportStatusEnum;
import com.guardfind.server.moderation.enums.ReportTargetTypeEnum;
import com.guardfind.server.moderation.model.resp.ReportPageResp;
import com.guardfind.server.moderation.model.resp.ReportResp;
import com.guardfind.server.notification.model.resp.AppMessageResp;
import com.guardfind.server.notification.model.resp.MarkReadResp;
import com.guardfind.server.notification.model.resp.MessagePageResp;
import com.guardfind.server.notification.model.resp.UnreadResp;
import com.guardfind.server.support.ContainerlessTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import top.continew.starter.json.jackson.autoconfigure.JacksonAutoConfiguration;
import top.continew.starter.json.jackson.autoconfigure.JacksonExtensionProperties;
import top.continew.starter.json.jackson.enums.BigNumberSerializeMode;
import top.continew.starter.web.model.R;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class Phase1JsonContractTest extends ContainerlessTestBase {

    private static final long SNOWFLAKE_ID = 1790000000000000001L;
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 9, 24, 11, 20, 5);
    private static final String TIME_TEXT = "2026-09-24 11:20:05";

    private final ObjectMapper mapper;

    Phase1JsonContractTest() {
        JacksonExtensionProperties properties = new JacksonExtensionProperties();
        properties.setBigNumberSerializeMode(BigNumberSerializeMode.FLEXIBLE);
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        new JacksonAutoConfiguration(properties).jackson2ObjectMapperBuilderCustomizer().customize(builder);
        mapper = builder.build();
    }

    @Test
    void enumsUseNames() throws Exception {
        enumNames(CaseStatusEnum.values());
        enumNames(UrgencyLevelEnum.values());
        enumNames(LeadStatusEnum.values());
        enumNames(LeadReplyFromEnum.values());
        enumNames(ReportStatusEnum.values());
        enumNames(ReportTargetTypeEnum.values());
    }

    @Test
    void flexibleModeAndCounts() throws Exception {
        JsonNode smallLong = json(7L);
        check(smallLong.isIntegralNumber() && smallLong
            .longValue() == 7L, "FLEXIBLE 必须保留未注解的小 Long 为数字，不能全局 TO_STRING");
        JsonNode largeLong = json(SNOWFLAKE_ID);
        check(largeLong.isTextual() && Long.toString(SNOWFLAKE_ID)
            .equals(largeLong.textValue()), "必须使用真实 FLEXIBLE 大数序列化器");
        JsonNode unread = json(new UnreadResp(7L));
        keys(unread, "unreadCount");
        number(unread, "unreadCount", 7L);
        JsonNode marked = json(new MarkReadResp(7));
        keys(marked, "markedCount");
        number(marked, "markedCount", 7L);
    }

    @ParameterizedTest
    @ValueSource(longs = {7L, SNOWFLAKE_ID})
    void userAndCaseContracts(long id) throws Exception {
        AppUserResp user = new AppUserResp();
        user.setId(id);
        user.setUnreadCount(7L);
        user.setCreatedAt(TIME);
        JsonNode userJson = json(user);
        text(userJson, "id", Long.toString(id));
        number(userJson, "unreadCount", 7L);
        text(userJson, "createdAt", TIME_TEXT);

        CasePublicResp publicCase = new CasePublicResp();
        publicCase.setId(id);
        publicCase.setMissingHours(72L);
        publicCase.setLeadCount(7);
        publicCase.setAgeAtMissing(6);
        publicCase.setHeight(118);
        publicCase.setMissingMonth("2024-08");
        publicCase.setStatus(CaseStatusEnum.PUBLISHED);
        publicCase.setUrgencyLevel(UrgencyLevelEnum.URGENT);
        publicCase.setPublishTime(TIME);
        JsonNode publicJson = json(publicCase);
        text(publicJson, "id", Long.toString(id));
        number(publicJson, "missingHours", 72L);
        number(publicJson, "leadCount", 7L);
        number(publicJson, "ageAtMissing", 6L);
        number(publicJson, "height", 118L);
        text(publicJson, "missingMonth", "2024-08");
        text(publicJson, "status", "PUBLISHED");
        text(publicJson, "urgencyLevel", "URGENT");
        text(publicJson, "publishTime", TIME_TEXT);
        check(!publicJson.has("relationType") && !publicJson.has("foundTime"), "公开视图不应新增详情字段");

        CaseDetailResp detail = new CaseDetailResp();
        detail.setId(id);
        detail.setMissingHours(72L);
        detail.setLeadCount(7);
        detail.setVersion(2);
        detail.setBirthday(LocalDate.of(2018, 5, 1));
        detail.setMissingTime(TIME);
        detail.setPublishTime(TIME);
        detail.setRelationType("PARENT");
        detail.setFoundTime(TIME);
        detail.setStatus(CaseStatusEnum.FOUND);
        JsonNode detailJson = json(detail);
        text(detailJson, "id", Long.toString(id));
        number(detailJson, "missingHours", 72L);
        number(detailJson, "leadCount", 7L);
        number(detailJson, "version", 2L);
        text(detailJson, "birthday", "2018-05-01");
        text(detailJson, "missingTime", TIME_TEXT);
        text(detailJson, "publishTime", TIME_TEXT);
        text(detailJson, "relationType", "PARENT");
        text(detailJson, "foundTime", TIME_TEXT);
        text(detailJson, "status", "FOUND");

        CaseStatusSummaryResp summary = new CaseStatusSummaryResp();
        summary.setDraft(7L);
        summary.setPendingReview(7L);
        summary.setPublished(7L);
        summary.setRejected(7L);
        summary.setArchived(7L);
        summary.setFound(7L);
        summary.setOffline(7L);
        JsonNode page = json(CaseMinePageResp.of(List.of(detail), 7L, summary));
        JsonNode record = pageRecord(page);
        text(record, "id", Long.toString(id));
        text(record, "relationType", "PARENT");
        text(record, "foundTime", TIME_TEXT);
        JsonNode summaryJson = page.path("summary");
        keys(summaryJson, "draft", "pendingReview", "published", "rejected", "archived", "found", "offline");
        for (String key : List.of("draft", "pendingReview", "published", "rejected", "archived", "found", "offline")) {
            number(summaryJson, key, 7L);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {7L, SNOWFLAKE_ID})
    void leadReplyReportMessageContracts(long id) throws Exception {
        LeadReplyResp reply = LeadReplyResp.of(id, "请提供照片", LeadReplyFromEnum.OWNER, TIME);
        JsonNode replyJson = json(reply);
        keys(replyJson, "id", "content", "from", "time");
        text(replyJson, "id", Long.toString(id));
        text(replyJson, "content", "请提供照片");
        text(replyJson, "from", "OWNER");
        text(replyJson, "time", TIME_TEXT);

        LeadResp lead = new LeadResp();
        lead.setId(id);
        lead.setCaseId(id);
        lead.setCaseName("张三");
        lead.setOccurTime(TIME);
        lead.setOccurAddress("曲周县");
        lead.setDescription("发现相似人员");
        lead.setMediaUrls(List.of("https://example.com/lead.jpg"));
        lead.setContactMode("IN_APP");
        lead.setContactValue("138****1234");
        lead.setSubmitterMasked("星友48**");
        lead.setStatus(LeadStatusEnum.CONTACTED);
        lead.setHighRisk(false);
        lead.setHandleRemark("已联系");
        lead.setReplies(List.of(reply));
        lead.setCreateTime(TIME);
        JsonNode leadJson = json(lead);
        keys(leadJson, "id", "caseId", "caseName", "occurTime", "occurAddress", "description", "mediaUrls", "contactMode", "contactValue", "submitterMasked", "status", "highRisk", "handleRemark", "replies", "createTime");
        text(leadJson, "id", Long.toString(id));
        text(leadJson, "caseId", Long.toString(id));
        text(leadJson, "status", "CONTACTED");
        text(leadJson, "contactMode", "IN_APP");
        text(leadJson, "contactValue", "138****1234");
        text(leadJson, "submitterMasked", "星友48**");
        text(leadJson, "occurTime", TIME_TEXT);
        text(leadJson, "createTime", TIME_TEXT);
        check(leadJson.path("highRisk").isBoolean() && !leadJson.path("highRisk")
            .booleanValue(), "highRisk 必须为布尔 false");
        check(leadJson.path("replies").isArray() && leadJson.path("replies").size() == 1, "replies 必须为回复数组");
        check(replyJson.equals(leadJson.path("replies").get(0)), "嵌套回复必须保持相同契约");
        check(leadJson.equals(pageRecord(json(LeadPageResp.of(List.of(lead), 7L)))), "线索分页记录契约不变");

        ReportResp report = new ReportResp();
        report.setId(id);
        report.setTargetId(id);
        report.setTargetType(ReportTargetTypeEnum.LEAD);
        report.setTargetName("线索摘要");
        report.setReasonType("FRAUD");
        report.setDescription("要求转账");
        report.setMediaUrls(List.of("https://example.com/report.jpg"));
        report.setStatus(ReportStatusEnum.APPROVED);
        report.setHitCount(7);
        report.setHandleRemark("已处理");
        report.setCreateTime(TIME);
        JsonNode reportJson = json(report);
        keys(reportJson, "id", "targetType", "targetId", "targetName", "reasonType", "description", "mediaUrls", "status", "hitCount", "handleRemark", "createTime");
        text(reportJson, "id", Long.toString(id));
        text(reportJson, "targetId", Long.toString(id));
        text(reportJson, "targetType", "LEAD");
        text(reportJson, "reasonType", "FRAUD");
        text(reportJson, "status", "APPROVED");
        number(reportJson, "hitCount", 7L);
        text(reportJson, "createTime", TIME_TEXT);
        check(reportJson.equals(pageRecord(json(ReportPageResp.of(List.of(report), 7L)))), "举报分页记录契约不变");

        AppMessageResp message = new AppMessageResp();
        message.setId(id);
        message.setType("CASE_LEAD");
        message.setTitle("收到新线索");
        message.setContent("请查看线索");
        message.setBizType("FIND_CASE");
        message.setBizId(id);
        message.setPath("/case/" + id + "/leads");
        message.setReadStatus(false);
        message.setCreateTime(TIME);
        JsonNode messageJson = json(message);
        keys(messageJson, "id", "type", "title", "content", "bizType", "bizId", "path", "readStatus", "createTime");
        text(messageJson, "id", Long.toString(id));
        text(messageJson, "bizId", Long.toString(id));
        text(messageJson, "type", "CASE_LEAD");
        text(messageJson, "bizType", "FIND_CASE");
        text(messageJson, "path", "/case/" + id + "/leads");
        text(messageJson, "createTime", TIME_TEXT);
        check(messageJson.path("readStatus").isBoolean() && !messageJson.path("readStatus")
            .booleanValue(), "readStatus 必须为布尔 false");
        check(messageJson.equals(pageRecord(json(MessagePageResp.of(List.of(message), 7L)))), "消息分页记录契约不变");
        message.setBizId(null);
        JsonNode nullableMessage = json(message);
        check(nullableMessage.has("bizId") && nullableMessage.get("bizId").isNull(), "nullable bizId 必须保留 JSON null");
        JsonNode nullableRecord = pageRecord(json(MessagePageResp.of(List.of(message), 7L)));
        check(nullableRecord.has("bizId") && nullableRecord.get("bizId").isNull(), "分页中的 nullable bizId 必须保留 null");
    }

    @Test
    void loginPolymorphismAndEnvelope() throws Exception {
        AppLoginReq phone = mapper.readValue("""
            {"authType":"PHONE","phone":"13800001234","code":"123456",
             "captchaId":null,"captchaValue":null,"platform":"WEB"}
            """, AppLoginReq.class);
        check(phone instanceof AppPhoneLoginReq, "PHONE 必须反序列化为短信请求");
        check("13800001234".equals(((AppPhoneLoginReq)phone).getPhone()), "手机号不能丢失");
        check("WEB".equals(phone.getPlatform()), "设备字段不能丢失");
        AppLoginReq account = mapper.readValue("""
            {"authType":"ACCOUNT","account":"13800001234","password":"test-password"}
            """, AppLoginReq.class);
        check(account instanceof AppAccountLoginReq, "ACCOUNT 必须反序列化为密码请求");
        check("test-password".equals(((AppAccountLoginReq)account).getPassword()), "密码字段不能丢失");

        AppLoginResp login = new AppLoginResp();
        login.setToken("test-token");
        login.setTokenName("Authorization");
        login.setExpiresIn(2592000L);
        login.setIsNewUser(true);
        login.setDeactivationCancelled(false);
        JsonNode envelope = json(R.ok(login));
        text(envelope, "code", "0");
        check(envelope.path("success").isBoolean() && envelope.path("success").booleanValue(), "成功信封必须为 true");
        JsonNode payload = envelope.path("data");
        text(payload, "token", "test-token");
        text(payload, "tokenName", "Authorization");
        number(payload, "expiresIn", 2592000L);
        check(payload.path("isNewUser").isBoolean() && payload.path("isNewUser").booleanValue(), "isNewUser 保持布尔命名");
        check(!payload.has("newUser"), "不能新增 newUser 别名");
        check(payload.path("deactivationCancelled").isBoolean() && !payload.path("deactivationCancelled")
            .booleanValue(), "注销取消状态必须为 false");
        check(json(R.ok()).path("data").isNull(), "无载荷动作必须返回 null");
    }

    private void enumNames(Enum<?>[] values) throws Exception {
        for (Enum<?> value : values) {
            JsonNode serialized = json(value);
            check(serialized.isTextual() && value.name().equals(serialized.textValue()), value.getDeclaringClass()
                .getSimpleName() + "." + value.name() + " 必须序列化为 name");
        }
    }

    private JsonNode json(Object value) throws Exception {
        return mapper.readTree(mapper.writeValueAsBytes(value));
    }

    private static JsonNode pageRecord(JsonNode page) {
        check(page.isObject() && !page.has("list"), "C 端分页不能出现 list: " + page);
        number(page, "total", 7L);
        JsonNode records = page.path("records");
        check(records.isArray() && records.size() == 1, "C 端分页必须包含 records 数组: " + page);
        return records.get(0);
    }

    private static void keys(JsonNode object, String... expected) {
        Set<String> actual = new HashSet<>();
        object.fieldNames().forEachRemaining(actual::add);
        check(object.isObject() && actual.equals(Set.of(expected)), "字段 key 不符合契约: " + object);
    }

    private static void text(JsonNode object, String key, String expected) {
        JsonNode value = object.path(key);
        check(value.isTextual() && expected.equals(value.textValue()), key + " 必须为字符串 " + expected + ": " + object);
    }

    private static void number(JsonNode object, String key, long expected) {
        JsonNode value = object.path(key);
        check(value.isIntegralNumber() && value.longValue() == expected, key + " 必须为数字 " + expected + ": " + object);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
