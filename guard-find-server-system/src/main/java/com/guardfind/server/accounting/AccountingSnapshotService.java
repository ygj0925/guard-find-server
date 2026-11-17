package com.guardfind.server.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardfind.server.accounting.mapper.AccountingSnapshotMapper;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.exception.AppBizException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

@Service
@RequiredArgsConstructor
public class AccountingSnapshotService {
    private final AccountingSnapshotMapper mapper;
    private final ObjectMapper objectMapper;

    public record Result(long version, String payload) {}

    public Result get() {
        var snapshot = mapper.find(AppUserContextHolder.getUserIdRequired());
        return snapshot == null ? new Result(0, null) : new Result(snapshot.getVersion(), snapshot.getPayload());
    }

    public Result save(long baseVersion, String operationId, String payload) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        if (baseVersion < 0 || baseVersion >= 9007199254740991L || operationId == null
            || !operationId.matches("[A-Za-z0-9-]{16,64}")) throw invalid("备份版本或操作 ID 无效");
        validate(payload);
        var existing = mapper.find(userId);
        if (existing != null && operationId.equals(existing.getOperationId())
            && existing.getVersion() == baseVersion + 1 && payload.equals(existing.getPayload()))
            return new Result(existing.getVersion(), existing.getPayload());
        if ((existing == null ? 0 : existing.getVersion()) != baseVersion) throw conflict();
        var next = new AccountingSnapshot();
        next.setUserId(userId); next.setVersion(baseVersion + 1); next.setPayload(payload); next.setOperationId(operationId);
        if (existing == null) {
            try { mapper.insertSnapshot(next); } catch (DuplicateKeyException e) { throw conflict(); }
        } else if (mapper.replace(next, baseVersion) != 1) throw conflict();
        return new Result(next.getVersion(), payload);
    }

    public Result patch(long baseVersion, String operationId, List<AccountingSnapshotController.Change> changes) {
        var current = mapper.find(AppUserContextHolder.getUserIdRequired());
        if (current == null) throw conflict();
        if (changes == null || changes.isEmpty() || changes.size() > 1000) throw invalid("同步批次无效");
        if (current.getVersion() != baseVersion && !(current.getVersion() == baseVersion + 1 && java.util.Objects.equals(operationId,current.getOperationId()))) throw conflict();
        try {
            var root = (ObjectNode) objectMapper.readTree(current.getPayload());
            var data = (ObjectNode) root.path("data");
            for (var change : changes) {
                if (change.collection().equals("settings")) {
                    if (!data.path("settings").has(change.key()) || change.value() == null || change.value().isNull()) throw invalid("设置字段无效");
                    ((ObjectNode)data.path("settings")).set(change.key(), change.value());
                    continue;
                }
                if (!Set.of("books", "accounts", "categories", "entries", "budgets", "recurring", "goals").contains(change.collection())) throw invalid("同步集合无效");
                var previous = data.path(change.collection());
                ArrayNode next = objectMapper.createArrayNode();
                boolean hasValue=change.value()!=null&&!change.value().isNull(),found=false;
                if(hasValue) {
                    if (!change.value().isObject() || !entityKey(change.collection(), change.value()).equals(change.key())) throw invalid("同步对象 ID 不匹配");
                }
                for(var item:previous) {if(entityKey(change.collection(),item).equals(change.key())){found=true;if(hasValue)next.add(change.value());}else next.add(item);}
                if(hasValue&&!found)next.add(change.value());
                data.set(change.collection(), next);
            }
            if (!isActiveBook(data)) {
                for (var book : data.path("books")) if (!book.path("archived").asBoolean(false)) { data.put("activeBookId", book.path("id").asText()); break; }
            }
            if (!isActiveBook(data)) throw invalid("至少保留一个未归档账本");
            return save(baseVersion, operationId, objectMapper.writeValueAsString(root));
        } catch (AppBizException e) { throw e; } catch (Exception e) { throw invalid("同步数据无效"); }
    }

    private boolean isActiveBook(JsonNode data) {
        for (var book : data.path("books")) if (book.path("id").asText().equals(data.path("activeBookId").asText()) && !book.path("archived").asBoolean(false)) return true;
        return false;
    }
    private String entityKey(String collection, JsonNode item) {
        if (collection.equals("budgets")) return objectMapper.createArrayNode().add(item.path("bookId").asText()).add(item.path("month").asText()).add(item.path("categoryId").asText()).toString();
        return item.path("id").asText();
    }

    private void validate(String payload) {
        if (payload == null || payload.length() > 5000000) throw invalid("备份为空或超过 5MB 字符");
        try {
            var root = objectMapper.readTree(payload);
            if (!root.path("format").asText().equals("jianzhang") || root.path("version").asInt() != 1) throw invalid("备份格式或版本不支持");
            var data = root.path("data");
            Set<String> books = ids(data, "books"), accounts = ids(data, "accounts"), categories = ids(data, "categories");
            ids(data, "entries"); ids(data, "recurring");
            if(data.has("goals")) ids(data,"goals");
            if (!data.path("budgets").isArray() || !data.path("settings").isObject()
                || !books.contains(data.path("activeBookId").asText())) throw invalid("备份结构无效");
            var settings=data.path("settings");
            if(settings.size()>0 && (settings.path("billingStartDay").asInt()<1||settings.path("billingStartDay").asInt()>31||!settings.path("hideAmounts").isBoolean()||!Set.of("light","dark").contains(settings.path("theme").asText())||!settings.path("accent").asText().matches("#[a-fA-F0-9]{6}")))throw invalid("账本设置无效");
            for(var goal:data.path("goals"))if(goal.path("name").asText().isBlank()||!goal.path("targetCents").isIntegralNumber()||goal.path("targetCents").asLong()<=0||!goal.path("savedCents").isIntegralNumber()||goal.path("savedCents").asLong()<0)throw invalid("存钱目标无效");
            for(var category:data.path("categories"))if(!category.path("name").isTextual()||category.path("name").asText().isBlank()||!Set.of("expense","income").contains(category.path("type").asText()))throw invalid("分类无效");
            for(var account:data.path("accounts"))if(!account.path("name").isTextual()||account.path("name").asText().isBlank()||!account.path("openingCents").isIntegralNumber()||!Set.of("cash","bank","credit").contains(account.path("kind").asText()))throw invalid("账户无效");
            for (var entry : data.path("entries")) {
                long amount = entry.path("cents").asLong(-1);
                String type = entry.path("type").asText();
                if (!entry.path("cents").isIntegralNumber() || amount < 1 || amount > 99999999999L
                    || !Set.of("expense", "income", "transfer").contains(type)
                    || !books.contains(entry.path("bookId").asText()) || !accounts.contains(entry.path("accountId").asText())
                    || !entry.path("tags").isArray() || entry.path("tags").size() > 20
                    || !entry.path("note").isTextual() || entry.path("note").asText().length() > 500) throw invalid("账单字段无效");
                LocalDate.parse(entry.path("date").asText());
                if(entry.has("original")) {
                    var original=entry.path("original");
                    if(!Set.of("USD","EUR","HKD","JPY","GBP","AUD","CAD","SGD").contains(original.path("currency").asText())||!original.path("cents").isIntegralNumber()||!original.path("rateMicros").isIntegralNumber()||!original.path("cents").canConvertToLong()||!original.path("rateMicros").canConvertToLong()||original.path("cents").asLong()<=0||original.path("rateMicros").asLong()<=0)throw invalid("原币或汇率无效");
                    var converted=java.math.BigInteger.valueOf(original.path("cents").asLong()).multiply(java.math.BigInteger.valueOf(original.path("rateMicros").asLong())).add(java.math.BigInteger.valueOf(500000)).divide(java.math.BigInteger.valueOf(1000000));
                    if(!converted.equals(java.math.BigInteger.valueOf(amount)))throw invalid("原币与入账金额不一致");
                }
                if (type.equals("transfer")) {
                    if (!accounts.contains(entry.path("toAccountId").asText()) || entry.path("toAccountId").equals(entry.path("accountId"))) throw invalid("转账账户无效");
                } else if (!categories.contains(entry.path("categoryId").asText())) throw invalid("账单分类无效");
                if(entry.has("attachments")){
                    var files=entry.path("attachments");if(!files.isArray()||files.size()>5)throw invalid("附件列表无效");
                    for(var file:files)if(!file.path("id").asText().matches("[a-zA-Z0-9-]{16,64}")||!file.path("name").isTextual()||file.path("size").asLong()<1||file.path("size").asLong()>5*1024*1024||!Set.of("image/png","image/jpeg","image/webp","application/pdf").contains(file.path("mime").asText()))throw invalid("附件元数据无效");
                }
            }
        } catch (AppBizException e) { throw e; } catch (Exception e) { throw invalid("备份 JSON 或日期无效"); }
    }

    private Set<String> ids(JsonNode data, String key) {
        if (!data.path(key).isArray()) throw invalid("备份缺少 " + key);
        Set<String> result = new HashSet<>();
        for (var item : data.path(key)) {
            var id = item.path("id");
            if (!id.isTextual() || id.asText().isBlank() || id.asText().length() > 128 || !result.add(id.asText())) throw invalid("备份 ID 无效或重复");
        }
        return result;
    }

    private AppBizException invalid(String message) { return new AppBizException(AppBizException.RULE_REJECTED, message); }
    private AppBizException conflict() { return new AppBizException(AppBizException.STATE_CONFLICT, "云端账本已更新，请先下载备份并处理冲突"); }
}
