package io.modelaudit.chatmodel.audit;

import io.modelaudit.chatmodel.audit.core.compliance.ComplianceProfile;
import io.modelaudit.chatmodel.audit.core.cost.CostCalculator;
import io.modelaudit.chatmodel.audit.core.cost.DefaultCostCalculator;
import io.modelaudit.chatmodel.audit.core.cost.ExchangeRateProvider;
import io.modelaudit.chatmodel.audit.core.cost.ModelPricingCatalog;
import io.modelaudit.chatmodel.audit.core.cost.StaticExchangeRateProvider;
import io.modelaudit.chatmodel.audit.core.resolver.TeamIdResolver;
import io.modelaudit.chatmodel.audit.core.resolver.UserIdResolver;
import io.modelaudit.chatmodel.audit.cost.YamlModelPricingCatalogLoader;
import io.modelaudit.chatmodel.audit.resolver.HeaderUserIdResolver;
import io.modelaudit.chatmodel.audit.resolver.MdcTeamIdResolver;
import io.modelaudit.chatmodel.audit.resolver.MdcUserIdResolver;
import io.modelaudit.chatmodel.audit.resolver.SpringSecurityUserIdResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.math.BigDecimal;

@AutoConfiguration
@ConditionalOnProperty(prefix = "audit.compliance", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(ComplianceAuditProperties.class)
public class ComplianceAuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ComplianceProfile complianceProfile(ComplianceAuditProperties props) {
        return ComplianceProfile.fromMode(props.getCompliance().getMode());
    }

    // user-id-resolver: spring-security(default) / mdc / header — 미지원 값은 spring-security로 폴백
    @Bean
    @ConditionalOnMissingBean
    UserIdResolver userIdResolver(ComplianceAuditProperties props) {
        String kind = props.getUserIdResolver();
        if (kind == null) {
            return new SpringSecurityUserIdResolver();
        }
        return switch (kind) {
            case "mdc" -> new MdcUserIdResolver(props.getUserIdMdcKey());
            case "header" -> new HeaderUserIdResolver(props.getUserIdHeaderName());
            default -> new SpringSecurityUserIdResolver();
        };
    }

    @Bean
    @ConditionalOnMissingBean
    TeamIdResolver teamIdResolver(ComplianceAuditProperties props) {
        return new MdcTeamIdResolver(props.getTeamIdMdcKey());
    }

    @Bean
    @ConditionalOnMissingBean
    ModelPricingCatalog modelPricingCatalog(ComplianceAuditProperties props, ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource(props.getCost().getTable());
        return YamlModelPricingCatalogLoader.load(resource);
    }

    @Bean
    @ConditionalOnMissingBean
    ExchangeRateProvider exchangeRateProvider(ComplianceAuditProperties props) {
        return new StaticExchangeRateProvider(BigDecimal.valueOf(props.getCost().getStaticUsdToKrw()));
    }

    @Bean
    @ConditionalOnMissingBean
    CostCalculator costCalculator(ModelPricingCatalog catalog, ExchangeRateProvider rate) {
        return new DefaultCostCalculator(catalog, rate);
    }
}
