package io.modelaudit.chatmodel.audit.actuator;

import io.modelaudit.chatmodel.audit.writer.AsyncBatchWriter;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

// vault 05 §5-6 — status UP/DOWN + details(queueDepth/queueCapacity/lastFlushAt/lastFlushDurationMs/dbReachable/spoolEnabled/spoolBacklog)
public class JdbcAuditHealthService implements AuditHealthService {

    private static final int DB_VALIDATE_TIMEOUT_SEC = 1;

    private final AsyncBatchWriter writer;
    private final DataSource dataSource;

    public JdbcAuditHealthService(AsyncBatchWriter writer, DataSource dataSource) {
        this.writer = writer;
        this.dataSource = dataSource;
    }

    @Override
    public Map<String, Object> health() {
        int queueDepth = writer.queueSize();
        int queueCapacity = writer.queueCapacity();
        boolean dbReachable = pingDb();
        Instant lastFlushAt = writer.lastFlushAt();
        long lastFlushDurationMs = writer.lastFlushDurationMs();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("queueDepth", queueDepth);
        details.put("queueCapacity", queueCapacity);
        details.put("lastFlushAt", lastFlushAt == null ? null : lastFlushAt.toString());
        details.put("lastFlushDurationMs", lastFlushDurationMs < 0L ? null : lastFlushDurationMs);
        details.put("dbReachable", dbReachable);
        // v0.4에서 디스크 spool 도입 예정 — v0.1은 항상 off
        details.put("spoolEnabled", false);
        details.put("spoolBacklog", 0L);

        boolean up = dbReachable && queueDepth < queueCapacity;
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", up ? "UP" : "DOWN");
        response.put("details", details);
        return response;
    }

    private boolean pingDb() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(DB_VALIDATE_TIMEOUT_SEC);
        } catch (SQLException e) {
            return false;
        }
    }
}
