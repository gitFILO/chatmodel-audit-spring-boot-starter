package io.modelaudit.chatmodel.audit.cost;

import io.modelaudit.chatmodel.audit.core.cost.ModelPricing;
import io.modelaudit.chatmodel.audit.core.cost.ModelPricingCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlModelPricingCatalogLoaderTest {

    @Test
    void loadsBundledTableWithTwelveModels() {
        ModelPricingCatalog catalog = YamlModelPricingCatalogLoader.load(new ClassPathResource("llm-cost-table.yml"));

        assertThat(catalog.size()).isEqualTo(12);
        assertThat(catalog.all())
            .extracting(ModelPricing::provider)
            .containsOnly("anthropic", "openai", "ollama");
    }

    @Test
    void looksUpAnthropicOpusPricing() {
        ModelPricingCatalog catalog = YamlModelPricingCatalogLoader.load(new ClassPathResource("llm-cost-table.yml"));

        ModelPricing pricing = catalog.lookup("anthropic", "claude-opus-4-7");

        assertThat(pricing).isNotNull();
        assertThat(pricing.inputPerMillionUsd()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(pricing.outputPerMillionUsd()).isEqualByComparingTo(new BigDecimal("75.00"));
    }

    @Test
    void ollamaModelsAreZeroCost() {
        ModelPricingCatalog catalog = YamlModelPricingCatalogLoader.load(new ClassPathResource("llm-cost-table.yml"));

        ModelPricing pricing = catalog.lookup("ollama", "llama3");

        assertThat(pricing.inputPerMillionUsd()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(pricing.outputPerMillionUsd()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void parsesInlineYaml() {
        String yaml = """
            models:
              - provider: anthropic
                model: claude-opus-4-7
                input-per-million-usd: 15
                output-per-million-usd: 75
            """;

        ModelPricingCatalog catalog = YamlModelPricingCatalogLoader.parse(
            new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))
        );

        assertThat(catalog.size()).isEqualTo(1);
        assertThat(catalog.lookup("anthropic", "claude-opus-4-7")).isNotNull();
    }

    @Test
    void rejectsMissingModelsKey() {
        String yaml = "exchange-rates:\n  usd-to-krw: 1380\n";

        assertThatThrownBy(() ->
            YamlModelPricingCatalogLoader.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)))
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
