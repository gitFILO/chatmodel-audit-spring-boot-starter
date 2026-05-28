package io.github.gitfilo.chatmodel.audit.actuator;

import java.util.Map;

// vault 05 §5-1 응답: totalCalls/totalTokensIn/totalTokensOut/totalCostKrw/errorRate/latency p50p95p99 + byTeam/byUser/byModel
public interface AuditStatsService {

    Map<String, Object> stats(String from, String to, String groupBy);
}
