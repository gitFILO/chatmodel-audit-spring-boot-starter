package io.github.gitfilo.chatmodel.audit.actuator;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;

import java.util.Map;

// vault 05 §5: stats / search / by-trace / health (read) + flag/{id} (write)
@Endpoint(id = "llm-audit")
public class LlmAuditEndpoint {

    private final AuditStatsService statsService;
    private final AuditSearchService searchService;
    private final AuditHealthService healthService;

    public LlmAuditEndpoint(AuditStatsService statsService,
                            AuditSearchService searchService,
                            AuditHealthService healthService) {
        this.statsService = statsService;
        this.searchService = searchService;
        this.healthService = healthService;
    }

    // GET /actuator/llm-audit/{stats|search|health} — query 파라미터로 분기
    @ReadOperation
    public Map<String, Object> read(@Selector String operation,
                                    String from,
                                    String to,
                                    String groupBy,
                                    String q,
                                    String user,
                                    String team,
                                    String provider,
                                    String model,
                                    String status,
                                    Integer limit,
                                    Integer offset) {
        return switch (operation) {
            case "stats" -> statsService.stats(from, to, groupBy);
            case "search" -> searchService.search(q, user, team, provider, model, status, from, to, limit, offset);
            case "health" -> healthService.health();
            default -> throw new IllegalArgumentException("Unknown llm-audit read operation: " + operation);
        };
    }

    // GET /actuator/llm-audit/by-trace/{traceId} — trace 단위 호출 체인
    @ReadOperation
    public Map<String, Object> readByTrace(@Selector String operation, @Selector String traceId) {
        if ("by-trace".equals(operation)) {
            return searchService.byTrace(traceId);
        }
        throw new IllegalArgumentException("Unknown llm-audit read operation: " + operation + "/" + traceId);
    }

    // POST /actuator/llm-audit/flag/{id} — 사후 환각 신고 (reason/reporter 본문)
    @WriteOperation
    public Map<String, Object> writeFlag(@Selector String operation,
                                         @Selector long id,
                                         String reason,
                                         String reporter) {
        if ("flag".equals(operation)) {
            return searchService.flagRecord(id, reason, reporter);
        }
        throw new IllegalArgumentException("Unknown llm-audit write operation: " + operation + "/" + id);
    }
}
