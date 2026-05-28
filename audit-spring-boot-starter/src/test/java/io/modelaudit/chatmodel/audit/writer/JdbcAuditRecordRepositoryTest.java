package io.modelaudit.chatmodel.audit.writer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelaudit.chatmodel.audit.ComplianceAuditProperties;
import io.modelaudit.chatmodel.audit.core.model.AuditRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAuditRecordRepositoryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EmbeddedDatabase ds;
    private JdbcTemplate jdbc;
    private ComplianceAuditProperties props;
    private JdbcAuditRecordRepository repo;

    @BeforeEach
    void setUp() {
        // H2 in-memory + Flyway 마이그레이션 파일 직접 적용 (V_audit_001 은 Flyway 버전 파서 비호환 → T5 검증 영역)
        ds = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName("audit-jdbc-" + UUID.randomUUID())
            .addScript("classpath:db/migration/h2/V_audit_001__create_llm_invocation_log.sql")
            .build();
        jdbc = new JdbcTemplate(ds);
        props = new ComplianceAuditProperties();
        repo = new JdbcAuditRecordRepository(ds, props);
    }

    @AfterEach
    void tearDown() {
        ds.shutdown();
    }

    @Test
    void batchInsert_one_hundred_records_persists_all_rows() {
        List<AuditRecord> records = new ArrayList<>(100);
        Instant base = Instant.parse("2026-06-15T10:00:00Z");
        for (int i = 0; i < 100; i++) {
            records.add(sample(base.plusSeconds(i), "trace-" + i, "u" + (i % 10), 50 + i));
        }

        repo.batchInsert(records);

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM llm_invocation_log", Long.class);
        assertThat(total).isEqualTo(100L);

        Long distinctTraces = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT trace_id) FROM llm_invocation_log", Long.class);
        assertThat(distinctTraces).isEqualTo(100L);
    }

    @Test
    void batchInsert_round_trips_all_scalar_columns() {
        Instant invokedAt = Instant.parse("2026-06-15T10:00:00Z");
        AuditRecord r = new AuditRecord(
            invokedAt, "trace-A", "span-A",
            "anthropic", "claude-opus-4-7", "alice", "alpha",
            "사용자 질문", "모델 응답", 1500, 800, 1234, 9_876_543L,
            AuditRecord.Status.SUCCESS, null, null, "stop",
            null, null,
            false, 0, false, false, "default"
        );

        repo.batchInsert(List.of(r));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM llm_invocation_log");
        assertThat(((Timestamp) row.get("invoked_at")).toInstant()).isEqualTo(invokedAt);
        assertThat(row.get("trace_id")).isEqualTo("trace-A");
        assertThat(row.get("span_id")).isEqualTo("span-A");
        assertThat(row.get("model_provider")).isEqualTo("anthropic");
        assertThat(row.get("model_name")).isEqualTo("claude-opus-4-7");
        assertThat(row.get("user_id")).isEqualTo("alice");
        assertThat(row.get("team_id")).isEqualTo("alpha");
        assertThat(row.get("prompt")).asString().contains("사용자 질문");
        assertThat(row.get("response")).asString().contains("모델 응답");
        assertThat(((Number) row.get("token_in")).intValue()).isEqualTo(1500);
        assertThat(((Number) row.get("token_out")).intValue()).isEqualTo(800);
        assertThat(((Number) row.get("latency_ms")).intValue()).isEqualTo(1234);
        assertThat(((Number) row.get("cost_micro_krw")).longValue()).isEqualTo(9_876_543L);
        assertThat(row.get("status")).isEqualTo("SUCCESS");
        assertThat(row.get("finish_reason")).isEqualTo("stop");
    }

    @Test
    void nullable_columns_persist_as_sql_null() {
        AuditRecord r = new AuditRecord(
            Instant.parse("2026-06-15T10:00:00Z"), "trace-B", null,
            "openai", "gpt-4o-mini", null, null,
            "p", null, null, null, 50, null,
            AuditRecord.Status.CANCELLED, null, null, null,
            null, null,
            false, 0, false, false, "default"
        );

        repo.batchInsert(List.of(r));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM llm_invocation_log");
        assertThat(row.get("span_id")).isNull();
        assertThat(row.get("user_id")).isNull();
        assertThat(row.get("team_id")).isNull();
        assertThat(row.get("response")).isNull();
        assertThat(row.get("token_in")).isNull();
        assertThat(row.get("token_out")).isNull();
        assertThat(row.get("cost_micro_krw")).isNull();
        assertThat(row.get("finish_reason")).isNull();
        assertThat(row.get("tool_calls_json")).isNull();
        assertThat(row.get("metadata_json")).isNull();
        assertThat(row.get("status")).isEqualTo("CANCELLED");
    }

    @Test
    void json_columns_round_trip_intact() throws Exception {
        String tools = "{\"calls\":[{\"name\":\"get_weather\",\"args\":{\"city\":\"Seoul\"}}]}";
        String meta = "{\"trace.parent\":\"abc\",\"region\":\"ap-northeast-2\",\"prompt.tokens\":42}";
        AuditRecord r = new AuditRecord(
            Instant.parse("2026-06-15T10:00:00Z"), "trace-J", "span-J",
            "anthropic", "claude-opus-4-7", "u", "t",
            "p", "r", 1, 2, 100, 10L,
            AuditRecord.Status.SUCCESS, null, null, "stop",
            tools, meta,
            false, 0, false, false, "default"
        );

        repo.batchInsert(List.of(r));

        // H2 JSON 컬럼 + setString 경로는 입력 JSON 텍스트를 JSON 문자열 값으로 래핑 저장 → decode 후 원본 트리 비교
        JsonNode toolsRoundTrip = decodeWrappedJson(readJsonBytes("tool_calls_json"));
        JsonNode metaRoundTrip = decodeWrappedJson(readJsonBytes("metadata_json"));
        assertThat(toolsRoundTrip).isEqualTo(MAPPER.readTree(tools));
        assertThat(metaRoundTrip).isEqualTo(MAPPER.readTree(meta));
        assertThat(toolsRoundTrip.path("calls").get(0).path("name").asText()).isEqualTo("get_weather");
        assertThat(metaRoundTrip.path("region").asText()).isEqualTo("ap-northeast-2");
        assertThat(metaRoundTrip.path("prompt.tokens").asInt()).isEqualTo(42);
    }

    @Test
    void batchInsert_one_hundred_with_json_payload_round_trips_each_row() throws Exception {
        List<AuditRecord> records = new ArrayList<>(100);
        Instant base = Instant.parse("2026-06-15T10:00:00Z");
        for (int i = 0; i < 100; i++) {
            String meta = "{\"i\":" + i + ",\"region\":\"ap-northeast-2\"}";
            records.add(new AuditRecord(
                base.plusSeconds(i), "trace-J-" + i, "span-J-" + i,
                "openai", "gpt-4o", "u", "t",
                "p" + i, "r" + i, 1, 2, 100, 10L,
                AuditRecord.Status.SUCCESS, null, null, "stop",
                null, meta,
                false, 0, false, false, "default"
            ));
        }

        repo.batchInsert(records);

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT trace_id, metadata_json FROM llm_invocation_log ORDER BY invoked_at");
        assertThat(rows).hasSize(100);
        for (int i = 0; i < 100; i++) {
            Map<String, Object> row = rows.get(i);
            assertThat(row.get("trace_id")).isEqualTo("trace-J-" + i);
            JsonNode meta = decodeWrappedJson((byte[]) row.get("metadata_json"));
            assertThat(meta.path("i").asInt()).isEqualTo(i);
            assertThat(meta.path("region").asText()).isEqualTo("ap-northeast-2");
        }
    }

    @Test
    void error_status_record_persists_error_class_and_message() {
        AuditRecord r = new AuditRecord(
            Instant.parse("2026-06-15T10:00:00Z"), "trace-E", "span-E",
            "openai", "gpt-4o", "u", "t",
            "p", null, null, null, 25, null,
            AuditRecord.Status.FAILED,
            "java.net.SocketTimeoutException", "read timed out", null,
            null, null,
            false, 0, false, false, "default"
        );

        repo.batchInsert(List.of(r));

        Map<String, Object> row = jdbc.queryForMap(
            "SELECT status, error_class, error_message FROM llm_invocation_log");
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("error_class")).isEqualTo("java.net.SocketTimeoutException");
        assertThat(row.get("error_message")).asString().contains("read timed out");
    }

    @Test
    void compliance_flags_and_profile_persist_for_kr_financial_path() {
        AuditRecord r = new AuditRecord(
            Instant.parse("2026-06-15T10:00:00Z"), "trace-K", "span-K",
            "anthropic", "claude-opus-4-7", "u", "t",
            "마스킹된 사용자 [MASKED:rrn:01]", "ack", 1, 2, 100, 10L,
            AuditRecord.Status.SUCCESS, null, null, "stop",
            null, null,
            true, 2, true, true, "kr-financial"
        );

        repo.batchInsert(List.of(r));

        Map<String, Object> row = jdbc.queryForMap(
            "SELECT pii_masked, masked_pii_count, external_sent, flagged, compliance_profile "
            + "FROM llm_invocation_log");
        assertThat(row.get("pii_masked")).isEqualTo(true);
        assertThat(((Number) row.get("masked_pii_count")).intValue()).isEqualTo(2);
        assertThat(row.get("external_sent")).isEqualTo(true);
        assertThat(row.get("flagged")).isEqualTo(true);
        assertThat(row.get("compliance_profile")).isEqualTo("kr-financial");
    }

    @Test
    void empty_list_does_not_execute_any_insert() {
        repo.batchInsert(List.of());
        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM llm_invocation_log", Long.class);
        assertThat(total).isZero();
    }

    @Test
    void custom_table_name_via_properties_targets_alternate_table() {
        jdbc.execute("""
            CREATE TABLE llm_audit_alt AS SELECT * FROM llm_invocation_log WHERE 1 = 0
            """);
        ComplianceAuditProperties altProps = new ComplianceAuditProperties();
        altProps.setTableName("llm_audit_alt");
        JdbcAuditRecordRepository altRepo = new JdbcAuditRecordRepository(ds, altProps);

        altRepo.batchInsert(List.of(sample(
            Instant.parse("2026-06-15T10:00:00Z"), "trace-X", "u", 100)));

        Long inAlt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM llm_audit_alt", Long.class);
        Long inDefault = jdbc.queryForObject(
            "SELECT COUNT(*) FROM llm_invocation_log", Long.class);
        assertThat(inAlt).isEqualTo(1L);
        assertThat(inDefault).isZero();
    }

    private byte[] readJsonBytes(String column) {
        return jdbc.queryForObject(
            "SELECT " + column + " FROM llm_invocation_log", byte[].class);
    }

    // H2 JSON 컬럼 + setString 경로: 입력 텍스트가 JSON 문자열 값으로 래핑되어 저장 → 한번 decode 해야 원본 트리
    private JsonNode decodeWrappedJson(byte[] bytes) {
        try {
            String raw = new String(bytes, StandardCharsets.UTF_8);
            String unwrapped = MAPPER.readValue(raw, String.class);
            return MAPPER.readTree(unwrapped);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private AuditRecord sample(Instant invokedAt, String traceId, String userId, int latencyMs) {
        return new AuditRecord(
            invokedAt, traceId, "span-" + traceId,
            "openai", "gpt-4o", userId, "team-a",
            "p-" + traceId, "r-" + traceId,
            10, 20, latencyMs, 1_000L,
            AuditRecord.Status.SUCCESS, null, null, "stop",
            null, null,
            false, 0, false, false, "default"
        );
    }
}
