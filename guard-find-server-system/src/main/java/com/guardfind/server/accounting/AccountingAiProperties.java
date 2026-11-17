package com.guardfind.server.accounting;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
@Data @Component @ConfigurationProperties(prefix="guardfind.accounting.ai")
public class AccountingAiProperties {
 private boolean enabled=false;
 private String apiBase="";
 private String apiKey="";
 private String visionModel="";
 private String transcriptionModel="";
}
