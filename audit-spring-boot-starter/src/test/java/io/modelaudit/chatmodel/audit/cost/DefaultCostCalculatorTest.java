package io.modelaudit.chatmodel.audit.cost;

import io.modelaudit.chatmodel.audit.core.cost.DefaultCostCalculator;
import io.modelaudit.chatmodel.audit.core.cost.ExchangeRateProvider;
import io.modelaudit.chatmodel.audit.core.cost.ModelPricing;
import io.modelaudit.chatmodel.audit.core.cost.ModelPricingCatalog;
import io.modelaudit.chatmodel.audit.core.cost.StaticExchangeRateProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultCostCalculatorTest {

    private final ModelPricingCatalog catalog = catalog(
        new ModelPricing("anthropic", "claude-opus-4-7", new BigDecimal("15.00"), new BigDecimal("75.00")),
        new ModelPricing("openai", "gpt-4o-mini", new BigDecimal("0.15"), new BigDecimal("0.60")),
        new ModelPricing("ollama", "llama3", BigDecimal.ZERO, BigDecimal.ZERO)
    );

    private final ExchangeRateProvider rate = new StaticExchangeRateProvider(1380L);

    private final DefaultCostCalculator calculator = new DefaultCostCalculator(catalog, rate);

    // 1000×15 + 500×75 = 52500 (USD·tokens/M) × 1380 = 72,450,000 micro KRW = 72.45 KRW
    @Test
    void computesKrwMicroForOpus() {
        Long microKrw = calculator.calculate("anthropic", "claude-opus-4-7", 1000, 500);

        assertThat(microKrw).isEqualTo(72_450_000L);
    }

    // 1,000,000×0.15 + 1,000,000×0.60 = 750,000 (USD·tokens/M) × 1380 = 1,035,000,000 micro KRW = 1035 KRW
    @Test
    void computesKrwMicroForMini() {
        Long microKrw = calculator.calculate("openai", "gpt-4o-mini", 1_000_000, 1_000_000);

        assertThat(microKrw).isEqualTo(1_035_000_000L);
    }

    @Test
    void ollamaCallsAreFree() {
        Long microKrw = calculator.calculate("ollama", "llama3", 10_000, 5_000);

        assertThat(microKrw).isZero();
    }

    @Test
    void unknownModelReturnsNull() {
        assertThat(calculator.calculate("openai", "gpt-9", 100, 50)).isNull();
    }

    @Test
    void nullTokensTreatedAsZero() {
        Long microKrw = calculator.calculate("anthropic", "claude-opus-4-7", null, null);

        assertThat(microKrw).isZero();
    }

    @Test
    void nullInputTokenOnlyOutput() {
        // 1000×75 × 1380 = 103,500,000
        Long microKrw = calculator.calculate("anthropic", "claude-opus-4-7", null, 1000);

        assertThat(microKrw).isEqualTo(103_500_000L);
    }

    // 1×15 + 0×75 = 15 × 1380 = 20700 micro KRW — 정수 자릿수 유지
    @Test
    void integerArithmeticPreservesPrecision() {
        Long microKrw = calculator.calculate("anthropic", "claude-opus-4-7", 1, 0);

        assertThat(microKrw).isEqualTo(20_700L);
    }

    private static ModelPricingCatalog catalog(ModelPricing... entries) {
        Map<String, ModelPricing> table = new LinkedHashMap<>();
        for (ModelPricing p : entries) {
            table.put(p.provider() + "/" + p.model(), p);
        }
        return new ModelPricingCatalog(table);
    }
}
