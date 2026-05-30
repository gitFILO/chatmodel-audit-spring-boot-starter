package io.modelaudit.chatmodel.audit.core.cost;

public interface CostCalculator {

    // Returns null when pricing or token count is unknown — DB allows cost_micro_krw NULL
    Long calculate(String provider, String model, Integer tokenIn, Integer tokenOut);
}
