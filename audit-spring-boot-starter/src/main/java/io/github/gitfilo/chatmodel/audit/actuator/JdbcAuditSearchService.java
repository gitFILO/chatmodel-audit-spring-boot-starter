package io.github.gitfilo.chatmodel.audit.actuator;

import io.github.gitfilo.chatmodel.audit.ComplianceAuditProperties;
import io.github.gitfilo.chatmodel.audit.core.compliance.ComplianceProfile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

// vault 05 §5-2/§5-3/§5-4 — q/user/team/provider/model/status 필터, by-trace 체인, flag 토글
public class JdbcAuditSearchService implements AuditSearchService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MIN_LIMIT = 1;
    private static final long DEFAULT_WINDOW_SECONDS = 30L * 86400L;

    // vault 06 §3 — 한국 PII 6종 단순 정규식 (v0.1, v0.2에서 별도 starter로 정밀 검증)
    private static final Pattern P_RRN = Pattern.compile("\\b\\d{6}[-\\s]?[1-4]\\d{6}\\b");
    private static final Pattern P_FOREIGNER = Pattern.compile("\\b\\d{6}[-\\s]?[5-8]\\d{6}\\b");
    private static final Pattern P_CARD = Pattern.compile("\\b\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}\\b");
    private static final Pattern P_BIZ = Pattern.compile("\\b\\d{3}[-\\s]?\\d{2}[-\\s]?\\d{5}\\b");
    private static final Pattern P_PHONE = Pattern.compile("\\b01\\d[-\\s]?\\d{3,4}[-\\s]?\\d{4}\\b");
    private static final Pattern P_EMAIL = Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}");

    private final JdbcTemplate jdbc;
    private final ComplianceAuditProperties props;
    private final ComplianceProfile profile;
    private final String table;

    public JdbcAuditSearchService(JdbcTemplate jdbc,
                                  ComplianceAuditProperties props,
                                  ComplianceProfile profile) {
        this.jdbc = jdbc;
        this.props = props;
        this.profile = profile;
        this.table = (props.getSchema() != null && !props.getSchema().isBlank())
            ? props.getSchema() + "." + props.getTableName()
            : props.getTableName();
    }

    @Override
    public Map<String, Object> search(String q,
                                      String user,
                                      String team,
                                      String provider,
                                      String model,
                                      String status,
                                      String from,
                                      String to,
                                      Integer limit,
                                      Integer offset) {
        Instant fromTs = parseFrom(from);
        Instant toTs = parseTo(to);
        int max = Math.max(MIN_LIMIT, props.getActuator().getMaxSearchResults());
        int pageLimit = clamp(limit == null ? DEFAULT_LIMIT : limit, MIN_LIMIT, max);
        int pageOffset = Math.max(0, offset == null ? 0 : offset);

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE invoked_at >= ? AND invoked_at <= ?");
        params.add(Timestamp.from(fromTs));
        params.add(Timestamp.from(toTs));

        if (q != null && !q.isBlank()) {
            String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
            where.append(" AND (LOWER(prompt) LIKE ? OR LOWER(response) LIKE ?)");
            params.add(like);
            params.add(like);
        }
        if (notBlank(user)) {
            where.append(" AND user_id = ?");
            params.add(user);
        }
        if (notBlank(team)) {
            where.append(" AND team_id = ?");
            params.add(team);
        }
        if (notBlank(provider)) {
            where.append(" AND model_provider = ?");
            params.add(provider);
        }
        if (notBlank(model)) {
            where.append(" AND model_name = ?");
            params.add(model);
        }
        if (notBlank(status)) {
            where.append(" AND status = ?");
            params.add(status.toUpperCase(Locale.ROOT));
        }

        String countSql = "SELECT COUNT(*) FROM " + table + " " + where;
        Long total = jdbc.queryForObject(countSql, Long.class, params.toArray());

        String selectSql = """
            SELECT id, invoked_at, trace_id, model_provider, model_name, user_id, team_id,
                   prompt, response, token_in, token_out, latency_ms, cost_micro_krw, status
            FROM %s %s
            ORDER BY invoked_at DESC, id DESC
            LIMIT ? OFFSET ?
            """.formatted(table, where);
        List<Object> selectParams = new ArrayList<>(params);
        selectParams.add(pageLimit);
        selectParams.add(pageOffset);

        RowMapper<Map<String, Object>> mapper = rowMapper(shouldMaskOutput());
        List<Map<String, Object>> results = jdbc.query(selectSql, mapper, selectParams.toArray());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", total == null ? 0L : total);
        response.put("limit", pageLimit);
        response.put("offset", pageOffset);
        response.put("from", fromTs.toString());
        response.put("to", toTs.toString());
        response.put("results", results);
        return response;
    }

    @Override
    public Map<String, Object> byTrace(String traceId) {
        if (!notBlank(traceId)) {
            throw new IllegalArgumentException("traceId is required");
        }
        String sql = """
            SELECT id, invoked_at, trace_id, model_provider, model_name, user_id, team_id,
                   prompt, response, token_in, token_out, latency_ms, cost_micro_krw, status
            FROM %s WHERE trace_id = ?
            ORDER BY invoked_at ASC, id ASC
            """.formatted(table);
        RowMapper<Map<String, Object>> mapper = rowMapper(shouldMaskOutput());
        List<Map<String, Object>> results = jdbc.query(sql, mapper, traceId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("traceId", traceId);
        response.put("count", (long) results.size());
        response.put("results", results);
        return response;
    }

    // v0.1 스키마는 reason/reporter 컬럼이 없어 flagged 토글만 영속화 (v0.2에서 별도 이력 테이블)
    @Override
    public Map<String, Object> flagRecord(long id, String reason, String reporter) {
        int updated = jdbc.update("UPDATE " + table + " SET flagged = TRUE WHERE id = ?", id);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("flagged", updated > 0);
        response.put("reason", reason);
        response.put("reporter", reporter);
        return response;
    }

    private boolean shouldMaskOutput() {
        return props.getActuator().getSearch().isMaskOutput() || profile.maskOutputOnSearch();
    }

    private static RowMapper<Map<String, Object>> rowMapper(boolean mask) {
        return (rs, rn) -> mapRow(rs, mask);
    }

    private static Map<String, Object> mapRow(ResultSet rs, boolean mask) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getLong("id"));
        Timestamp ts = rs.getTimestamp("invoked_at");
        m.put("invokedAt", ts == null ? null : ts.toInstant().toString());
        m.put("traceId", rs.getString("trace_id"));
        m.put("modelProvider", rs.getString("model_provider"));
        m.put("modelName", rs.getString("model_name"));
        m.put("userId", rs.getString("user_id"));
        m.put("teamId", rs.getString("team_id"));
        String prompt = rs.getString("prompt");
        String response = rs.getString("response");
        m.put("prompt", mask ? maskPii(prompt) : prompt);
        m.put("response", mask ? maskPii(response) : response);
        m.put("tokenIn", nullableInt(rs, "token_in"));
        m.put("tokenOut", nullableInt(rs, "token_out"));
        m.put("latencyMs", rs.getInt("latency_ms"));
        Object cost = rs.getObject("cost_micro_krw");
        m.put("costKrw", cost == null ? null : ((Number) cost).longValue() / 1_000_000L);
        m.put("status", rs.getString("status"));
        return m;
    }

    static String maskPii(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        String r = P_RRN.matcher(s).replaceAll("[MASKED:rrn:01]");
        r = P_FOREIGNER.matcher(r).replaceAll("[MASKED:fgnid:08]");
        r = P_CARD.matcher(r).replaceAll("[MASKED:card:03]");
        r = P_BIZ.matcher(r).replaceAll("[MASKED:bizno:04]");
        r = P_PHONE.matcher(r).replaceAll("[MASKED:tel:05]");
        r = P_EMAIL.matcher(r).replaceAll("[MASKED:email:06]");
        return r;
    }

    private static Integer nullableInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    static Instant parseFrom(String s) {
        if (!notBlank(s)) {
            return Instant.now().minusSeconds(DEFAULT_WINDOW_SECONDS);
        }
        return parseInstant(s, false);
    }

    static Instant parseTo(String s) {
        if (!notBlank(s)) {
            return Instant.now();
        }
        return parseInstant(s, true);
    }

    private static Instant parseInstant(String s, boolean endOfDayIfDate) {
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException ignored) {
            // ISO instant 실패 시 LocalDate 시도
        }
        try {
            LocalDate d = LocalDate.parse(s);
            LocalTime t = endOfDayIfDate ? LocalTime.of(23, 59, 59) : LocalTime.MIDNIGHT;
            return d.atTime(t).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date/time: " + s, e);
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
