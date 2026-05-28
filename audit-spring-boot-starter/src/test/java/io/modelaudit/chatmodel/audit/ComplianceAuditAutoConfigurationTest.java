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
import io.modelaudit.chatmodel.audit.metrics.AuditMicrometerMetrics;
import io.modelaudit.chatmodel.audit.writer.AsyncBatchWriter;
import io.modelaudit.chatmodel.audit.writer.JdbcAuditRecordRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComplianceAuditAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    ComplianceAuditAutoConfiguration.class))
            .withUserConfiguration(MeterRegistryConfig.class)
            .withPropertyValues(
                    "audit.compliance.enabled=true",
                    "spring.datasource.url=jdbc:h2:mem:cw1;DB_CLOSE_DELAY=-1",
                    "spring.datasource.driver-class-name=org.h2.Driver");

    @Test
    void disabled_by_default_no_beans_registered() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ComplianceAuditAutoConfiguration.class))
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ComplianceAuditProperties.class));
    }

    @Test
    void default_mode_picks_default_profile() {
        runner.run(ctx -> {
            assertThat(ctx.getBean("defaultProfile", DefaultProfile.class)).isNotNull();
            assertThat(ctx.getBean("krFinancialProfile", KrFinancialProfile.class)).isNotNull();
            ComplianceProfile primary = ctx.getBean(ComplianceProfile.class);
            assertThat(primary.name()).isEqualTo("default");
            assertThat(primary.piiMaskEnabled()).isFalse();
        });
    }

    @Test
    void kr_financial_mode_picks_kr_financial_profile() {
        runner.withPropertyValues("audit.compliance.compliance.mode=kr-financial")
                .run(ctx -> {
                    ComplianceProfile primary = ctx.getBean(ComplianceProfile.class);
                    assertThat(primary.name()).isEqualTo("kr-financial");
                    assertThat(primary.piiMaskEnabled()).isTrue();
                    assertThat(primary.retentionDays()).isEqualTo(1825);
                });
    }

    @Test
    void unknown_mode_falls_back_to_default_profile() {
        runner.withPropertyValues("audit.compliance.compliance.mode=nonexistent")
                .run(ctx -> {
                    ComplianceProfile primary = ctx.getBean(ComplianceProfile.class);
                    assertThat(primary.name()).isEqualTo("default");
                });
    }

    @Test
    void external_profile_bean_is_selected_by_mode() {
        runner.withUserConfiguration(ExternalProfileConfig.class)
                .withPropertyValues("audit.compliance.compliance.mode=us-financial")
                .run(ctx -> {
                    ComplianceProfile primary = ctx.getBean(ComplianceProfile.class);
                    assertThat(primary.name()).isEqualTo("us-financial");
                });
    }

    @Test
    void korean_pii_detectors_all_registered() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(KoreanResidentNoDetector.class);
            assertThat(ctx).hasSingleBean(KoreanForeignerIdDetector.class);
            assertThat(ctx).hasSingleBean(KoreanCardDetector.class);
            assertThat(ctx).hasSingleBean(KoreanBusinessNoDetector.class);
            assertThat(ctx).hasSingleBean(KoreanPhoneDetector.class);
            assertThat(ctx).hasSingleBean(EmailDetector.class);
            List<PiiDetector> detectors = ctx.getBeanProvider(PiiDetector.class).orderedStream().toList();
            assertThat(detectors).hasSize(6);
        });
    }

    @Test
    void pii_mask_service_inactive_under_default_profile() {
        runner.run(ctx -> {
            PiiMaskService svc = ctx.getBean(PiiMaskService.class);
            assertThat(svc.activeIds()).isEmpty();
            String input = "주민번호 900101-1234567";
            assertThat(svc.mask(input)).isEqualTo(input);
        });
    }

    @Test
    void pii_mask_service_active_under_kr_financial_profile() {
        runner.withPropertyValues("audit.compliance.compliance.mode=kr-financial")
                .run(ctx -> {
                    PiiMaskService svc = ctx.getBean(PiiMaskService.class);
                    assertThat(svc.activeIds()).containsExactly(
                            "kr-resident-no", "kr-foreigner-id", "kr-card",
                            "kr-business-no", "kr-phone", "email");
                    assertThat(svc.registry()).containsKeys(
                            "kr-resident-no", "kr-foreigner-id", "kr-card",
                            "kr-business-no", "kr-phone", "email");
                });
    }

    @Test
    void infra_beans_registered_with_datasource_and_meter_registry() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(AuditMicrometerMetrics.class);
            assertThat(ctx).hasSingleBean(JdbcAuditRecordRepository.class);
            assertThat(ctx).hasSingleBean(AsyncBatchWriter.class);
        });
    }

    @Configuration
    static class MeterRegistryConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Configuration
    static class ExternalProfileConfig {
        @Bean
        ComplianceProfile usFinancialProfile() {
            return new ComplianceProfile() {
                @Override public String name() { return "us-financial"; }
                @Override public int retentionDays() { return 2555; }
                @Override public boolean piiMaskEnabled() { return true; }
                @Override public List<String> piiProviders() { return List.of("email"); }
                @Override public boolean maskOutputOnSearch() { return true; }
                @Override public io.modelaudit.chatmodel.audit.core.compliance.CostCurrency costCurrency() {
                    return io.modelaudit.chatmodel.audit.core.compliance.CostCurrency.USD;
                }
            };
        }
    }

}
