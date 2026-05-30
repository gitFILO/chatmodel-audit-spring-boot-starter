package io.modelaudit.chatmodel.audit;

import io.modelaudit.chatmodel.audit.core.compliance.ComplianceProfile;
import io.modelaudit.chatmodel.audit.core.compliance.DefaultProfile;
import io.modelaudit.chatmodel.audit.core.compliance.KrFinancialProfile;
import io.modelaudit.chatmodel.audit.core.compliance.pii.EmailDetector;
import io.modelaudit.chatmodel.audit.core.compliance.pii.KoreanBusinessNoDetector;
import io.modelaudit.chatmodel.audit.core.compliance.pii.KoreanCardDetector;
import io.modelaudit.chatmodel.audit.core.compliance.pii.KoreanForeignerIdDetector;
import io.modelaudit.chatmodel.audit.core.compliance.pii.KoreanPhoneDetector;
import io.modelaudit.chatmodel.audit.core.compliance.pii.KoreanResidentNoDetector;
import io.modelaudit.chatmodel.audit.core.compliance.pii.PiiDetector;
import io.modelaudit.chatmodel.audit.core.compliance.pii.PiiMaskService;
import io.modelaudit.chatmodel.audit.core.cost.CostCalculator;
import io.modelaudit.chatmodel.audit.core.cost.DefaultCostCalculator;
import io.modelaudit.chatmodel.audit.core.cost.ExchangeRateProvider;
import io.modelaudit.chatmodel.audit.core.cost.ModelPricingCatalog;
import io.modelaudit.chatmodel.audit.core.cost.StaticExchangeRateProvider;
import io.modelaudit.chatmodel.audit.core.resolver.TeamIdResolver;
import io.modelaudit.chatmodel.audit.core.resolver.UserIdResolver;
import io.modelaudit.chatmodel.audit.cost.YamlModelPricingCatalogLoader;
import io.modelaudit.chatmodel.audit.metrics.AuditMicrometerMetrics;
import io.modelaudit.chatmodel.audit.resolver.HeaderUserIdResolver;
import io.modelaudit.chatmodel.audit.resolver.MdcTeamIdResolver;
import io.modelaudit.chatmodel.audit.resolver.MdcUserIdResolver;
import io.modelaudit.chatmodel.audit.resolver.SpringSecurityUserIdResolver;
import io.modelaudit.chatmodel.audit.writer.AsyncBatchWriter;
import io.modelaudit.chatmodel.audit.writer.JdbcAuditRecordRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;

@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnProperty(prefix = "audit.compliance", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(ComplianceAuditProperties.class)
public class ComplianceAuditAutoConfiguration {

    // user-id-resolver: spring-security(default) / mdc / header — unknown values fall back to spring-security
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

    // External starter registers its own ComplianceProfile @Bean and activates via the single props.compliance.mode line
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "complianceProfile")
    ComplianceProfile complianceProfile(ComplianceAuditProperties props,
                                        ObjectProvider<List<ComplianceProfile>> profilesProvider) {
        List<ComplianceProfile> profiles = profilesProvider.getIfAvailable(List::of);
        String mode = props.getCompliance().getMode();
        if (mode != null) {
            for (ComplianceProfile p : profiles) {
                if (mode.equals(p.name())) {
                    return p;
                }
            }
        }
        return DefaultProfile.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean(DefaultProfile.class)
    DefaultProfile defaultProfile() {
        return DefaultProfile.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean(KrFinancialProfile.class)
    KrFinancialProfile krFinancialProfile() {
        return KrFinancialProfile.INSTANCE;
    }

    // 6 built-in PII detectors — external starter additions via the PiiDetector interface are auto-discovered
    @Bean
    @ConditionalOnMissingBean(KoreanResidentNoDetector.class)
    KoreanResidentNoDetector koreanResidentNoDetector() {
        return new KoreanResidentNoDetector();
    }

    @Bean
    @ConditionalOnMissingBean(KoreanForeignerIdDetector.class)
    KoreanForeignerIdDetector koreanForeignerIdDetector() {
        return new KoreanForeignerIdDetector();
    }

    @Bean
    @ConditionalOnMissingBean(KoreanCardDetector.class)
    KoreanCardDetector koreanCardDetector() {
        return new KoreanCardDetector();
    }

    @Bean
    @ConditionalOnMissingBean(KoreanBusinessNoDetector.class)
    KoreanBusinessNoDetector koreanBusinessNoDetector() {
        return new KoreanBusinessNoDetector();
    }

    @Bean
    @ConditionalOnMissingBean(KoreanPhoneDetector.class)
    KoreanPhoneDetector koreanPhoneDetector() {
        return new KoreanPhoneDetector();
    }

    @Bean
    @ConditionalOnMissingBean(EmailDetector.class)
    EmailDetector emailDetector() {
        return new EmailDetector();
    }

    // Active detector set = profile.piiMaskEnabled() ? profile.piiProviders() : []
    @Bean
    @ConditionalOnMissingBean
    PiiMaskService piiMaskService(ObjectProvider<List<PiiDetector>> detectorsProvider,
                                  ComplianceProfile profile) {
        List<PiiDetector> detectors = detectorsProvider.getIfAvailable(List::of);
        List<String> active = profile.piiMaskEnabled() ? profile.piiProviders() : List.of();
        return new PiiMaskService(detectors, active);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MeterRegistry.class)
    AuditMicrometerMetrics auditMicrometerMetrics(MeterRegistry registry) {
        return new AuditMicrometerMetrics(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DataSource.class)
    JdbcAuditRecordRepository auditRepository(DataSource dataSource, ComplianceAuditProperties props) {
        return new JdbcAuditRecordRepository(dataSource, props);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({JdbcAuditRecordRepository.class, AuditMicrometerMetrics.class})
    AsyncBatchWriter asyncBatchWriter(JdbcAuditRecordRepository repository,
                                      ComplianceAuditProperties props,
                                      AuditMicrometerMetrics metrics) {
        return new AsyncBatchWriter(repository, props, metrics);
    }
}
