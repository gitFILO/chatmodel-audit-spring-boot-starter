package io.github.gitfilo.chatmodel.audit;

import io.github.gitfilo.chatmodel.audit.core.compliance.ComplianceProfile;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "audit.compliance", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(ComplianceAuditProperties.class)
public class ComplianceAuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ComplianceProfile complianceProfile(ComplianceAuditProperties props) {
        return ComplianceProfile.fromMode(props.getCompliance().getMode());
    }
}
