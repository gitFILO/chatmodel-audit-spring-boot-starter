package io.github.gitfilo.chatmodel.audit.core.cost;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class DefaultCostCalculator implements CostCalculator {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000L);

    private final ModelPricingCatalog catalog;
    private final ExchangeRateProvider exchangeRate;

    public DefaultCostCalculator(ModelPricingCatalog catalog, ExchangeRateProvider exchangeRate) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.exchangeRate = Objects.requireNonNull(exchangeRate, "exchangeRate");
    }

    // micro KRW = (token_in × inputPerMUSD + token_out × outputPerMUSD) × usdToKrw — 1M factor가 micro 변환과 상쇄
    @Override
    public Long calculate(String provider, String model, Integer tokenIn, Integer tokenOut) {
        ModelPricing pricing = catalog.lookup(provider, model);
        if (pricing == null) {
            return null;
        }
        int in = tokenIn == null ? 0 : tokenIn;
        int out = tokenOut == null ? 0 : tokenOut;
        if (in == 0 && out == 0) {
            return 0L;
        }
        BigDecimal rate = exchangeRate.usdToKrw();
        if (rate == null || rate.signum() <= 0) {
            return null;
        }
        BigDecimal totalUsdPerMillion = pricing.inputPerMillionUsd().multiply(BigDecimal.valueOf(in))
            .add(pricing.outputPerMillionUsd().multiply(BigDecimal.valueOf(out)));
        return totalUsdPerMillion.multiply(rate).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
