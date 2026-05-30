package io.modelaudit.chatmodel.audit.cost;

import io.modelaudit.chatmodel.audit.core.cost.ModelPricing;
import io.modelaudit.chatmodel.audit.core.cost.ModelPricingCatalog;
import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// llm-cost-table.yml -> ModelPricingCatalog loader (uses snakeyaml 2.x, spring-boot-starter dependency)
public final class YamlModelPricingCatalogLoader {

    private YamlModelPricingCatalogLoader() {
    }

    public static ModelPricingCatalog load(Resource resource) {
        if (resource == null || !resource.exists()) {
            throw new IllegalArgumentException("pricing resource not found: " + resource);
        }
        try (InputStream is = resource.getInputStream()) {
            return parse(is);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load pricing yaml: " + resource, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static ModelPricingCatalog parse(InputStream yaml) {
        Map<String, Object> root = new Yaml().load(yaml);
        if (root == null) {
            throw new IllegalArgumentException("empty pricing yaml");
        }
        Object modelsNode = root.get("models");
        if (!(modelsNode instanceof List<?> rawList)) {
            throw new IllegalArgumentException("missing 'models' list in pricing yaml");
        }
        Map<String, ModelPricing> table = new LinkedHashMap<>();
        for (Object entry : rawList) {
            if (!(entry instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("pricing entry must be a map: " + entry);
            }
            ModelPricing pricing = toPricing((Map<String, Object>) map);
            table.put(pricing.provider() + "/" + pricing.model(), pricing);
        }
        return new ModelPricingCatalog(table);
    }

    private static ModelPricing toPricing(Map<String, Object> map) {
        String provider = requireString(map, "provider");
        String model = requireString(map, "model");
        BigDecimal input = requireDecimal(map, "input-per-million-usd");
        BigDecimal output = requireDecimal(map, "output-per-million-usd");
        return new ModelPricing(provider, model, input, output);
    }

    private static String requireString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            throw new IllegalArgumentException("missing '" + key + "' in entry: " + map);
        }
        return v.toString();
    }

    private static BigDecimal requireDecimal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            throw new IllegalArgumentException("missing '" + key + "' in entry: " + map);
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number num) {
            return new BigDecimal(num.toString());
        }
        return new BigDecimal(v.toString());
    }
}
