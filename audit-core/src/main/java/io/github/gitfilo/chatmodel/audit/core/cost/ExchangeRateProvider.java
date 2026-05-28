package io.github.gitfilo.chatmodel.audit.core.cost;

import java.math.BigDecimal;

public interface ExchangeRateProvider {

    BigDecimal usdToKrw();
}
