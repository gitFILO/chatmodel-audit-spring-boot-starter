package io.modelaudit.chatmodel.audit.core.cost;

import java.math.BigDecimal;
import java.util.Objects;

public final class StaticExchangeRateProvider implements ExchangeRateProvider {

    private final BigDecimal usdToKrw;

    public StaticExchangeRateProvider(BigDecimal usdToKrw) {
        this.usdToKrw = Objects.requireNonNull(usdToKrw, "usdToKrw");
    }

    public StaticExchangeRateProvider(long usdToKrw) {
        this(BigDecimal.valueOf(usdToKrw));
    }

    @Override
    public BigDecimal usdToKrw() {
        return usdToKrw;
    }
}
