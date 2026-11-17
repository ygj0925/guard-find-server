package com.guardfind.server.accounting;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.time.*;
import java.util.*;
public final class AccountingRecurringCalculator {
 private AccountingRecurringCalculator(){}
 public static boolean advance(ObjectNode root,LocalDate today){
  var data=root.path("data");if(!data.path("entries").isArray()||!data.path("recurring").isArray())return false;
  var entries=(ArrayNode)data.path("entries");Set<String>keys=new HashSet<>();for(var e:entries)if(e.hasNonNull("recurringKey"))keys.add(e.path("recurringKey").asText());
  boolean changed=false;
  for(var raw:data.path("recurring")){
   if(!raw.path("enabled").asBoolean(false)||!raw.isObject())continue;
   if(data.path("books").isArray()) { boolean active=false; for(var b:data.path("books"))if(b.path("id").asText().equals(raw.path("template").path("bookId").asText())&&!b.path("archived").asBoolean(false))active=true; if(!active)continue; }
   var rule=(ObjectNode)raw;var next=LocalDate.parse(rule.path("nextDate").asText());int count=0;
   while(!next.isAfter(today)&&count++<1000){
    String key=rule.path("id").asText()+":"+next;
    if(keys.add(key)){
     if(!rule.path("template").isObject())throw new IllegalArgumentException("周期模板无效");
     var entry=((ObjectNode)rule.path("template")).deepCopy();entry.put("id","recurring:"+key);entry.put("date",next.toString());entry.put("recurringKey",key);entry.put("updatedAt",Instant.now().toString());entry.remove("deletedAt");entries.add(entry);
    }
    String frequency=rule.path("frequency").asText();
    next=switch(frequency){case"daily"->next.plusDays(1);case"weekly"->next.plusWeeks(1);case"monthly"->{var month=YearMonth.from(next).plusMonths(1);int day=rule.path("day").asInt();if(day<1||day>31)throw new IllegalArgumentException("周期日期无效");yield month.atDay(Math.min(day,month.lengthOfMonth()));}default->throw new IllegalArgumentException("周期无效");};
    changed=true;
   }
   rule.put("nextDate",next.toString());
  }
  return changed;
 }
}
