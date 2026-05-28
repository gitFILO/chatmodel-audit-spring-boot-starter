package io.github.gitfilo.chatmodel.audit.actuator;

import java.util.Map;

// vault 05 §5-6 — queueDepth/queueCapacity/lastFlushAt/lastFlushDurationMs/dbReachable/spoolEnabled/spoolBacklog
public interface AuditHealthService {

    Map<String, Object> health();
}
