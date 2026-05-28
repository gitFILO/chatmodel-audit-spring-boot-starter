package io.modelaudit.chatmodel.audit.actuator;

import io.modelaudit.chatmodel.audit.ComplianceAuditAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = ComplianceAuditAutoConfiguration.class)
@ConditionalOnClass(Endpoint.class)
@ConditionalOnProperty(prefix = "audit.compliance", name = "enabled", havingValue = "true", matchIfMissing = false)
public class LlmAuditActuatorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({AuditStatsService.class, AuditSearchService.class, AuditHealthService.class})
    @ConditionalOnAvailableEndpoint(endpoint = LlmAuditEndpoint.class)
    LlmAuditEndpoint llmAuditEndpoint(AuditStatsService statsService,
                                      AuditSearchService searchService,
                                      AuditHealthService healthService) {
        return new LlmAuditEndpoint(statsService, searchService, healthService);
    }
}
