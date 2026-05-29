package io.modelaudit.chatmodel.audit.actuator;

import java.util.Map;

// vault 05 §5-2/§5-3/§5-4 — search/byTrace/flag 3 작업. PII 재마스킹은 구현체에서.
public interface AuditSearchService {

    Map<String, Object> search(String q,
                               String user,
                               String team,
                               String provider,
                               String model,
                               String status,
                               String from,
                               String to,
                               Integer limit,
                               Integer offset);

    Map<String, Object> byTrace(String traceId);

    // 사후 환각 신고 — flagged=true + reason/reporter 영속화
    Map<String, Object> flagRecord(long id, String reason, String reporter);
}
