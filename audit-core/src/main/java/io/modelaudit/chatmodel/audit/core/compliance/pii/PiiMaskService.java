package io.modelaudit.chatmodel.audit.core.compliance.pii;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PiiMaskService {

    private final Map<String, PiiDetector> registry;
    private final List<String> activeIds;

    public PiiMaskService(List<PiiDetector> detectors, List<String> activeIds) {
        Objects.requireNonNull(detectors, "detectors");
        Objects.requireNonNull(activeIds, "activeIds");
        Map<String, PiiDetector> map = new LinkedHashMap<>();
        // On duplicate id, the later-registered detector wins — intentional for external starter override
        for (PiiDetector d : detectors) {
            if (d != null && d.id() != null) {
                map.put(d.id(), d);
            }
        }
        this.registry = Map.copyOf(map);
        this.activeIds = List.copyOf(activeIds);
    }

    public String mask(String input) {
        if (input == null || input.isEmpty() || activeIds.isEmpty()) {
            return input;
        }
        String result = input;
        for (String id : activeIds) {
            PiiDetector d = registry.get(id);
            if (d != null) {
                result = d.mask(result);
            }
        }
        return result;
    }

    public Map<String, PiiDetector> registry() {
        return registry;
    }

    public List<String> activeIds() {
        return activeIds;
    }
}
