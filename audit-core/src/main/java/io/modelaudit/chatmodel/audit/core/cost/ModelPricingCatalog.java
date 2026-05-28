package io.modelaudit.chatmodel.audit.core.cost;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

// provider/model lookup 카탈로그 — provider/model 키 정규화는 소문자 trim
public final class ModelPricingCatalog {

    private final Map<String, ModelPricing> table;

    public ModelPricingCatalog(Map<String, ModelPricing> table) {
        Objects.requireNonNull(table, "table");
        Map<String, ModelPricing> normalized = new LinkedHashMap<>();
        for (ModelPricing pricing : table.values()) {
            normalized.put(key(pricing.provider(), pricing.model()), pricing);
        }
        this.table = Collections.unmodifiableMap(normalized);
    }

    public ModelPricing lookup(String provider, String model) {
        if (provider == null || model == null) {
            return null;
        }
        return table.get(key(provider, model));
    }

    public int size() {
        return table.size();
    }

    public Collection<ModelPricing> all() {
        return table.values();
    }

    private static String key(String provider, String model) {
        return provider.trim().toLowerCase(Locale.ROOT) + "/" + model.trim().toLowerCase(Locale.ROOT);
    }
}
