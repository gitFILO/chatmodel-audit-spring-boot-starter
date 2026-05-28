package io.github.gitfilo.chatmodel.audit.actuator;

import io.github.gitfilo.chatmodel.audit.ComplianceAuditProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcAuditStatsServiceTest {

    private EmbeddedDatabase ds;
    private JdbcTemplate jdbc;
    private JdbcAuditStatsService svc;

    @BeforeEach
    void setUp() {
        ds = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName("audit-stats-" + UUID.randomUUID())
            .addScript("classpath:db/migration/h2/V_audit_001__create_llm_invocation_log.sql")
            .build();
        jdbc = new JdbcTemplate(ds);
        svc = new JdbcAuditStatsService(jdbc, new ComplianceAuditProperties());
    }

    @AfterEach
    void tearDown() {
        ds.shutdown();
    }

    @Test
    void empty_table_returns_zero_totals() {
        Map<String, Object> result = svc.stats("2026-06-01", "2026-06-30", "team");

        assertThat(result.get("totalCalls")).isEqualTo(0L);
        assertThat(result.get("totalTokensIn")).isEqualTo(0L);
        assertThat(result.get("totalTokensOut")).isEqualTo(0L);
        assertThat(result.get("totalCostKrw")).isEqualTo(0L);
        assertThat(result.get("errorRate")).isEqualTo(0.0);
        assertThat(result.get("avgLatencyMs")).isEqualTo(0L);
        assertThat(result.get("p95LatencyMs")).isEqualTo(0L);
        assertThat(result.get("p99LatencyMs")).isEqualTo(0L);
        assertThat(result.get("from")).isEqualTo("2026-06-01T00:00:00Z");
        assertThat(result.get("to")).isEqualTo("2026-06-30T23:59:59Z");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byTeam = (List<Map<String, Object>>) result.get("byTeam");
        assertThat(byTeam).isEmpty();
    }

    @Test
    void groupBy_team_aggregates_calls_cost_and_heavy_pct() {
        Instant base = Instant.parse("2026-06-15T10:00:00Z");
        // team alpha: 3 calls, cost 6,000,000 micro KRW (=6 KRW total)
        insert(base, "alpha", "u1", "openai", "gpt-4o", 100, 2_000_000L, "SUCCESS");
        insert(base.plusSeconds(10), "alpha", "u2", "openai", "gpt-4o", 150, 2_000_000L, "SUCCESS");
        insert(base.plusSeconds(20), "alpha", "u1", "openai", "gpt-4o", 200, 2_000_000L, "SUCCESS");
        // team beta: 1 call, cost 1,000,000 micro KRW (=1 KRW)
        insert(base.plusSeconds(30), "beta", "u3", "anthropic", "claude-haiku-4-5", 50, 1_000_000L, "SUCCESS");
        // team null → "unknown"
        insert(base.plusSeconds(40), null, "u4", "ollama", "qwen2.5", 80, 0L, "SUCCESS");

        Map<String, Object> result = svc.stats("2026-06-01", "2026-06-30", "team");

        assertThat(result.get("totalCalls")).isEqualTo(5L);
        assertThat(result.get("totalCostKrw")).isEqualTo(7L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byTeam = (List<Map<String, Object>>) result.get("byTeam");
        assertThat(byTeam).hasSize(3);
        // 비용 내림차순 정렬: alpha → beta → unknown
        assertThat(byTeam.get(0).get("teamId")).isEqualTo("alpha");
        assertThat(byTeam.get(0).get("calls")).isEqualTo(3L);
        assertThat(byTeam.get(0).get("costKrw")).isEqualTo(6L);
        assertThat((Double) byTeam.get(0).get("heavyPct")).isEqualTo(round4(6.0 / 7.0));

        assertThat(byTeam.get(1).get("teamId")).isEqualTo("beta");
        assertThat(byTeam.get(1).get("calls")).isEqualTo(1L);
        assertThat((Double) byTeam.get(1).get("heavyPct")).isEqualTo(round4(1.0 / 7.0));

        assertThat(byTeam.get(2).get("teamId")).isEqualTo("unknown");
        assertThat(byTeam.get(2).get("calls")).isEqualTo(1L);
        assertThat((Double) byTeam.get(2).get("heavyPct")).isEqualTo(0.0);
    }

    @Test
    void groupBy_user_returns_byUser_key() {
        Instant base = Instant.parse("2026-06-15T10:00:00Z");
        insert(base, "team-a", "alice", "openai", "gpt-4o", 100, 3_000_000L, "SUCCESS");
        insert(base.plusSeconds(10), "team-a", "alice", "openai", "gpt-4o", 100, 3_000_000L, "SUCCESS");
        insert(base.plusSeconds(20), "team-a", "bob", "openai", "gpt-4o", 100, 1_000_000L, "SUCCESS");

        Map<String, Object> result = svc.stats("2026-06-01", "2026-06-30", "user");

        assertThat(result).containsKey("byUser");
        assertThat(result).doesNotContainKey("byTeam");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byUser = (List<Map<String, Object>>) result.get("byUser");
        assertThat(byUser).hasSize(2);
        assertThat(byUser.get(0).get("userId")).isEqualTo("alice");
        assertThat(byUser.get(0).get("calls")).isEqualTo(2L);
        assertThat(byUser.get(0).get("costKrw")).isEqualTo(6L);
        assertThat(byUser.get(1).get("userId")).isEqualTo("bob");
    }

    @Test
    void groupBy_model_returns_provider_and_name_without_heavyPct() {
        Instant base = Instant.parse("2026-06-15T10:00:00Z");
        insert(base, "team-a", "u1", "ollama", "qwen2.5-coder:7b", 50, 0L, "SUCCESS");
        insert(base.plusSeconds(5), "team-a", "u1", "ollama", "qwen2.5-coder:7b", 50, 0L, "SUCCESS");
        insert(base.plusSeconds(10), "team-a", "u1", "anthropic", "claude-opus-4-7", 200, 5_000_000L, "SUCCESS");

        Map<String, Object> result = svc.stats("2026-06-01", "2026-06-30", "model");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byModel = (List<Map<String, Object>>) result.get("byModel");
        assertThat(byModel).hasSize(2);
        assertThat(byModel.get(0).get("provider")).isEqualTo("ollama");
        assertThat(byModel.get(0).get("name")).isEqualTo("qwen2.5-coder:7b");
        assertThat(byModel.get(0).get("calls")).isEqualTo(2L);
        assertThat(byModel.get(0).get("costKrw")).isEqualTo(0L);
        assertThat(byModel.get(0)).doesNotContainKey("heavyPct");
        assertThat(byModel.get(1).get("provider")).isEqualTo("anthropic");
        assertThat(byModel.get(1).get("name")).isEqualTo("claude-opus-4-7");
        assertThat(byModel.get(1).get("costKrw")).isEqualTo(5L);
    }

    @Test
    void from_to_window_filters_out_of_range_rows() {
        insert(Instant.parse("2026-05-31T23:59:00Z"), "alpha", "u1", "openai", "gpt-4o", 100, 0L, "SUCCESS");
        insert(Instant.parse("2026-06-15T10:00:00Z"), "alpha", "u1", "openai", "gpt-4o", 100, 0L, "SUCCESS");
        insert(Instant.parse("2026-07-01T00:00:01Z"), "alpha", "u1", "openai", "gpt-4o", 100, 0L, "SUCCESS");

        Map<String, Object> result = svc.stats("2026-06-01", "2026-06-30", "team");

        assertThat(result.get("totalCalls")).isEqualTo(1L);
    }

    @Test
    void error_rate_counts_non_success_status() {
        Instant base = Instant.parse("2026-06-15T10:00:00Z");
        insert(base, "alpha", "u1", "openai", "gpt-4o", 100, 0L, "SUCCESS");
        insert(base.plusSeconds(1), "alpha", "u1", "openai", "gpt-4o", 100, 0L, "SUCCESS");
        insert(base.plusSeconds(2), "alpha", "u1", "openai", "gpt-4o", 100, 0L, "FAILED");
        insert(base.plusSeconds(3), "alpha", "u1", "openai", "gpt-4o", 100, 0L, "CANCELLED");

        Map<String, Object> result = svc.stats("2026-06-01", "2026-06-30", "team");

        assertThat(result.get("totalCalls")).isEqualTo(4L);
        assertThat(result.get("errorRate")).isEqualTo(round4(2.0 / 4.0));
    }

    @Test
    void latency_percentiles_reflect_distribution() {
        Instant base = Instant.parse("2026-06-15T10:00:00Z");
        // 100 records with latencies 1..100
        for (int i = 1; i <= 100; i++) {
            insert(base.plusSeconds(i), "alpha", "u" + i, "openai", "gpt-4o", i, 0L, "SUCCESS");
        }

        Map<String, Object> result = svc.stats("2026-06-01", "2026-06-30", "team");

        long p95 = (Long) result.get("p95LatencyMs");
        long p99 = (Long) result.get("p99LatencyMs");
        long avg = (Long) result.get("avgLatencyMs");
        assertThat(avg).isBetween(49L, 51L);
        assertThat(p95).isBetween(94L, 96L);
        assertThat(p99).isBetween(98L, 100L);
    }

    @Test
    void unknown_groupBy_falls_back_to_team() {
        Instant base = Instant.parse("2026-06-15T10:00:00Z");
        insert(base, "alpha", "u1", "openai", "gpt-4o", 100, 0L, "SUCCESS");

        Map<String, Object> result = svc.stats("2026-06-01", "2026-06-30", "unsupported");

        assertThat(result).containsKey("byTeam");
    }

    @Test
    void null_groupBy_defaults_to_team() {
        Instant base = Instant.parse("2026-06-15T10:00:00Z");
        insert(base, "alpha", "u1", "openai", "gpt-4o", 100, 0L, "SUCCESS");

        Map<String, Object> result = svc.stats("2026-06-01", "2026-06-30", null);

        assertThat(result).containsKey("byTeam");
    }

    @Test
    void instant_from_to_accepted_as_well() {
        Instant base = Instant.parse("2026-06-15T10:00:00Z");
        insert(base, "alpha", "u1", "openai", "gpt-4o", 100, 0L, "SUCCESS");

        Map<String, Object> result = svc.stats("2026-06-15T00:00:00Z", "2026-06-15T23:59:59Z", "team");

        assertThat(result.get("totalCalls")).isEqualTo(1L);
        assertThat(result.get("from")).isEqualTo("2026-06-15T00:00:00Z");
        assertThat(result.get("to")).isEqualTo("2026-06-15T23:59:59Z");
    }

    @Test
    void invalid_date_throws() {
        assertThatThrownBy(() -> svc.stats("not-a-date", null, "team"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private void insert(Instant invokedAt, String teamId, String userId,
                        String provider, String model,
                        int latencyMs, long costMicroKrw, String status) {
        jdbc.update("""
            INSERT INTO llm_invocation_log (
                invoked_at, trace_id, model_provider, model_name,
                user_id, team_id, prompt, response,
                token_in, token_out, latency_ms, cost_micro_krw,
                status, compliance_profile
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
            Timestamp.from(invokedAt),
            UUID.randomUUID().toString(),
            provider, model,
            userId, teamId, "p", "r",
            10, 20, latencyMs, costMicroKrw,
            status, "default"
        );
    }

    private static double round4(double d) {
        return Math.round(d * 10000.0) / 10000.0;
    }
}
