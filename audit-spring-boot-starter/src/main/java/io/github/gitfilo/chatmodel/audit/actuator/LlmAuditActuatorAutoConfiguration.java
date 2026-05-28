package io.github.gitfilo.chatmodel.audit.actuator;

import io.github.gitfilo.chatmodel.audit.ComplianceAuditAutoConfiguration;
import io.github.gitfilo.chatmodel.audit.ComplianceAuditProperties;
import io.github.gitfilo.chatmodel.audit.core.compliance.ComplianceProfile;
import io.github.gitfilo.chatmodel.audit.writer.AsyncBatchWriter;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@AutoConfiguration(after = ComplianceAuditAutoConfiguration.class)
@ConditionalOnClass(Endpoint.class)
@ConditionalOnProperty(prefix = "audit.compliance", name = "enabled", havingValue = "true", matchIfMissing = false)
public class LlmAuditActuatorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuditStatsService.class)
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnProperty(prefix = "audit.compliance.actuator", name = "stats-enabled", havingValue = "true", matchIfMissing = true)
    AuditStatsService auditStatsService(JdbcTemplate jdbcTemplate, ComplianceAuditProperties props) {
        return new JdbcAuditStatsService(jdbcTemplate, props);
    }

    @Bean
    @ConditionalOnMissingBean(AuditSearchService.class)
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnProperty(prefix = "audit.compliance.actuator", name = "search-enabled", havingValue = "true", matchIfMissing = true)
    AuditSearchService auditSearchService(JdbcTemplate jdbcTemplate, ComplianceAuditProperties props) {
        ComplianceProfile profile = ComplianceProfile.fromMode(props.getCompliance().getMode());
        return new JdbcAuditSearchService(jdbcTemplate, props, profile);
    }

    @Bean
    @ConditionalOnMissingBean(AuditHealthService.class)
    @ConditionalOnBean({AsyncBatchWriter.class, DataSource.class})
    AuditHealthService auditHealthService(AsyncBatchWriter writer, DataSource dataSource) {
        return new JdbcAuditHealthService(writer, dataSource);
    }

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
