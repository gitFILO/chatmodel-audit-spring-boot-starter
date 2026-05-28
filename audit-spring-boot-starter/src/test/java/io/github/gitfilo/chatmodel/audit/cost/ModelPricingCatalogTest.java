package io.github.gitfilo.chatmodel.audit.cost;

import io.github.gitfilo.chatmodel.audit.core.cost.ModelPricing;
import io.github.gitfilo.chatmodel.audit.core.cost.ModelPricingCatalog;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModelPricingCatalogTest {

    @Test
    void lookupIsCaseInsensitive() {
        ModelPricingCatalog catalog = catalogOf(new ModelPricing(
            "Anthropic", "Claude-Opus-4-7", new BigDecimal("15"), new BigDecimal("75")
        ));

        assertThat(catalog.lookup("anthropic", "claude-opus-4-7")).isNotNull();
        assertThat(catalog.lookup("ANTHROPIC", "CLAUDE-OPUS-4-7")).isNotNull();
        assertThat(catalog.lookup(" anthropic ", " claude-opus-4-7 ")).isNotNull();
    }

    @Test
    void returnsNullForUnknownModel() {
        ModelPricingCatalog catalog = catalogOf(new ModelPricing(
            "anthropic", "claude-opus-4-7", BigDecimal.ONE, BigDecimal.ONE
        ));

        assertThat(catalog.lookup("openai", "gpt-9")).isNull();
        assertThat(catalog.lookup(null, "anything")).isNull();
        assertThat(catalog.lookup("openai", null)).isNull();
    }

    private static ModelPricingCatalog catalogOf(ModelPricing... entries) {
        Map<String, ModelPricing> table = new LinkedHashMap<>();
        for (ModelPricing p : entries) {
            table.put(p.provider() + "/" + p.model(), p);
        }
        return new ModelPricingCatalog(table);
    }
}
