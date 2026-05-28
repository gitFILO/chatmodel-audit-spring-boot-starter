package io.github.gitfilo.chatmodel.audit.core.cost;

public interface CostCalculator {

    // 단가 미정/토큰 미정 시 null 반환 — DB는 cost_micro_krw NULL 허용
    Long calculate(String provider, String model, Integer tokenIn, Integer tokenOut);
}
