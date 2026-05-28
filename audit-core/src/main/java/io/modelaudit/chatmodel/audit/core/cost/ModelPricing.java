package io.modelaudit.chatmodel.audit.core.cost;

import java.math.BigDecimal;

// 토큰 1M 당 USD 단가 — 입력/출력 분리
public record ModelPricing(
    String provider,
    String model,
    BigDecimal inputPerMillionUsd,
    BigDecimal outputPerMillionUsd
) {
    public ModelPricing {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model blank");
        }
        if (inputPerMillionUsd == null || inputPerMillionUsd.signum() < 0) {
            throw new IllegalArgumentException("inputPerMillionUsd invalid: " + inputPerMillionUsd);
        }
        if (outputPerMillionUsd == null || outputPerMillionUsd.signum() < 0) {
            throw new IllegalArgumentException("outputPerMillionUsd invalid: " + outputPerMillionUsd);
        }
    }
}
