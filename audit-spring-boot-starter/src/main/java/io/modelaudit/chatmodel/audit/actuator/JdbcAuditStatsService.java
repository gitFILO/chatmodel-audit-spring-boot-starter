package io.modelaudit.chatmodel.audit.actuator;

import io.modelaudit.chatmodel.audit.ComplianceAuditProperties;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// vault 05 §5-1 — totals + p95/p99 + byTeam/byUser/byModel Top10. from/to는 ISO date 또는 instant
public class JdbcAuditStatsService implements AuditStatsService {

    private static final int TOP_N = 10;
    private static final long DEFAULT_WINDOW_SECONDS = 30L * 86400L;
    private static final String UNKNOWN = "unknown";

    private final JdbcTemplate jdbc;
    private final String table;

    public JdbcAuditStatsService(JdbcTemplate jdbc, ComplianceAuditProperties props) {
        this.jdbc = jdbc;
        this.table = (props.getSchema() != null && !props.getSchema().isBlank())
            ? props.getSchema() + "." + props.getTableName()
            : props.getTableName();
    }

    @Override
    public Map<String, Object> stats(String from, String to, String groupBy) {
        Instant fromTs = parseFrom(from);
        Instant toTs = parseTo(to);
        String key = normalizeGroup(groupBy);

        Aggregate agg = loadAggregate(fromTs, toTs);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("from", fromTs.toString());
        response.put("to", toTs.toString());
        response.put("totalCalls", agg.totalCalls);
        response.put("totalTokensIn", agg.totalTokensIn);
        response.put("totalTokensOut", agg.totalTokensOut);
        response.put("totalCostKrw", agg.totalCostMicroKrw / 1_000_000L);
        response.put("errorRate", agg.totalCalls == 0 ? 0.0 : round4((double) agg.errorCalls / agg.totalCalls));
        response.put("avgLatencyMs", Math.round(agg.avgLatency));
        response.put("p95LatencyMs", Math.round(agg.p95Latency));
        response.put("p99LatencyMs", Math.round(agg.p99Latency));

        switch (key) {
            case "team" -> response.put("byTeam", byTeam(fromTs, toTs, agg.totalCostMicroKrw));
            case "user" -> response.put("byUser", byUser(fromTs, toTs, agg.totalCostMicroKrw));
            case "model" -> response.put("byModel", byModel(fromTs, toTs));
            default -> response.put("byTeam", byTeam(fromTs, toTs, agg.totalCostMicroKrw));
        }
        return response;
    }

    private Aggregate loadAggregate(Instant from, Instant to) {
        String sql = """
            SELECT
                COUNT(*) AS total_calls,
                COALESCE(SUM(token_in), 0) AS total_tokens_in,
                COALESCE(SUM(token_out), 0) AS total_tokens_out,
                COALESCE(SUM(cost_micro_krw), 0) AS total_cost_micro_krw,
                COALESCE(SUM(CASE WHEN status <> 'SUCCESS' THEN 1 ELSE 0 END), 0) AS error_calls,
                COALESCE(AVG(latency_ms), 0) AS avg_latency,
                COALESCE(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms), 0) AS p95,
                COALESCE(PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY latency_ms), 0) AS p99
            FROM %s
            WHERE invoked_at >= ? AND invoked_at <= ?
            """.formatted(table);
        return jdbc.queryForObject(sql, (rs, rn) -> new Aggregate(
            rs.getLong("total_calls"),
            rs.getLong("total_tokens_in"),
            rs.getLong("total_tokens_out"),
            rs.getLong("total_cost_micro_krw"),
            rs.getLong("error_calls"),
            rs.getDouble("avg_latency"),
            rs.getDouble("p95"),
            rs.getDouble("p99")
        ), Timestamp.from(from), Timestamp.from(to));
    }

    private List<Map<String, Object>> byTeam(Instant from, Instant to, long totalCostMicroKrw) {
        String sql = """
            SELECT COALESCE(team_id, '%s') AS k,
                   COUNT(*) AS calls,
                   COALESCE(SUM(cost_micro_krw), 0) AS cost_micro
            FROM %s
            WHERE invoked_at >= ? AND invoked_at <= ?
            GROUP BY COALESCE(team_id, '%s')
            ORDER BY cost_micro DESC, calls DESC, k ASC
            FETCH FIRST %d ROWS ONLY
            """.formatted(UNKNOWN, table, UNKNOWN, TOP_N);
        return jdbc.query(sql, (rs, rn) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            long costMicro = rs.getLong("cost_micro");
            m.put("teamId", rs.getString("k"));
            m.put("calls", rs.getLong("calls"));
            m.put("costKrw", costMicro / 1_000_000L);
            m.put("heavyPct", totalCostMicroKrw == 0 ? 0.0 : round4((double) costMicro / totalCostMicroKrw));
            return m;
        }, Timestamp.from(from), Timestamp.from(to));
    }

    private List<Map<String, Object>> byUser(Instant from, Instant to, long totalCostMicroKrw) {
        String sql = """
            SELECT COALESCE(user_id, '%s') AS k,
                   COUNT(*) AS calls,
                   COALESCE(SUM(cost_micro_krw), 0) AS cost_micro
            FROM %s
            WHERE invoked_at >= ? AND invoked_at <= ?
            GROUP BY COALESCE(user_id, '%s')
            ORDER BY cost_micro DESC, calls DESC, k ASC
            FETCH FIRST %d ROWS ONLY
            """.formatted(UNKNOWN, table, UNKNOWN, TOP_N);
        return jdbc.query(sql, (rs, rn) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            long costMicro = rs.getLong("cost_micro");
            m.put("userId", rs.getString("k"));
            m.put("calls", rs.getLong("calls"));
            m.put("costKrw", costMicro / 1_000_000L);
            m.put("heavyPct", totalCostMicroKrw == 0 ? 0.0 : round4((double) costMicro / totalCostMicroKrw));
            return m;
        }, Timestamp.from(from), Timestamp.from(to));
    }

    private List<Map<String, Object>> byModel(Instant from, Instant to) {
        String sql = """
            SELECT model_provider AS provider,
                   model_name AS name,
                   COUNT(*) AS calls,
                   COALESCE(SUM(cost_micro_krw), 0) AS cost_micro
            FROM %s
            WHERE invoked_at >= ? AND invoked_at <= ?
            GROUP BY model_provider, model_name
            ORDER BY calls DESC, cost_micro DESC, provider ASC, name ASC
            FETCH FIRST %d ROWS ONLY
            """.formatted(table, TOP_N);
        return jdbc.query(sql, (rs, rn) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("provider", rs.getString("provider"));
            m.put("name", rs.getString("name"));
            m.put("calls", rs.getLong("calls"));
            m.put("costKrw", rs.getLong("cost_micro") / 1_000_000L);
            return m;
        }, Timestamp.from(from), Timestamp.from(to));
    }

    static Instant parseFrom(String s) {
        if (s == null || s.isBlank()) {
            return Instant.now().minusSeconds(DEFAULT_WINDOW_SECONDS);
        }
        return parseInstant(s, false);
    }

    static Instant parseTo(String s) {
        if (s == null || s.isBlank()) {
            return Instant.now();
        }
        return parseInstant(s, true);
    }

    private static Instant parseInstant(String s, boolean endOfDayIfDate) {
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException ignored) {
            // ISO instant 아님 — LocalDate 시도
        }
        try {
            LocalDate d = LocalDate.parse(s);
            LocalTime t = endOfDayIfDate ? LocalTime.of(23, 59, 59) : LocalTime.MIDNIGHT;
            return d.atTime(t).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date/time: " + s, e);
        }
    }

    private static String normalizeGroup(String g) {
        if (g == null || g.isBlank()) {
            return "team";
        }
        String lower = g.trim().toLowerCase();
        return switch (lower) {
            case "team", "user", "model" -> lower;
            default -> "team";
        };
    }

    private static double round4(double d) {
        return Math.round(d * 10000.0) / 10000.0;
    }

    private record Aggregate(
        long totalCalls,
        long totalTokensIn,
        long totalTokensOut,
        long totalCostMicroKrw,
        long errorCalls,
        double avgLatency,
        double p95Latency,
        double p99Latency
    ) { }
}
