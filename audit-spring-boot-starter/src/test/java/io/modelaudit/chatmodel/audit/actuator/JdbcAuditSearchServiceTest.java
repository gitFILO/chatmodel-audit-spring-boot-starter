package io.modelaudit.chatmodel.audit.actuator;

import io.modelaudit.chatmodel.audit.ComplianceAuditProperties;
import io.modelaudit.chatmodel.audit.core.compliance.ComplianceProfile;
import io.modelaudit.chatmodel.audit.core.compliance.DefaultProfile;
import io.modelaudit.chatmodel.audit.core.compliance.KrFinancialProfile;
import io.modelaudit.chatmodel.audit.core.compliance.pii.EmailDetector;
import io.modelaudit.chatmodel.audit.core.compliance.pii.KoreanBusinessNoDetector;
import io.modelaudit.chatmodel.audit.core.compliance.pii.KoreanCardDetector;
import io.modelaudit.chatmodel.audit.core.compliance.pii.KoreanForeignerIdDetector;
import io.modelaudit.chatmodel.audit.core.compliance.pii.KoreanPhoneDetector;
import io.modelaudit.chatmodel.audit.core.compliance.pii.KoreanResidentNoDetector;
import io.modelaudit.chatmodel.audit.core.compliance.pii.PiiDetector;
import io.modelaudit.chatmodel.audit.core.compliance.pii.PiiMaskService;
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

class JdbcAuditSearchServiceTest {

    private EmbeddedDatabase ds;
    private JdbcTemplate jdbc;
    private ComplianceAuditProperties props;
    private List<PiiDetector> detectors;

    @BeforeEach
    void setUp() {
        ds = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName("audit-search-" + UUID.randomUUID())
            .addScript("classpath:db/migration/h2/V_audit_001__create_llm_invocation_log.sql")
            .build();
        jdbc = new JdbcTemplate(ds);
        props = new ComplianceAuditProperties();
        detectors = List.of(
            new KoreanResidentNoDetector(),
            new KoreanForeignerIdDetector(),
            new KoreanCardDetector(),
            new KoreanBusinessNoDetector(),
            new KoreanPhoneDetector(),
            new EmailDetector()
        );
    }

    @AfterEach
    void tearDown() {
        ds.shutdown();
    }

    private JdbcAuditSearchService svc(ComplianceProfile profile) {
        List<String> active = profile.piiMaskEnabled() ? profile.piiProviders() : List.of();
        PiiMaskService mask = new PiiMaskService(detectors, active);
        return new JdbcAuditSearchService(jdbc, props, profile, mask);
    }

    // 정규식 하드코딩 금지를 입증 — 마스킹을 항상 적용하는 detector를 주입해서 위임 동작만 검증
    private JdbcAuditSearchService svcWithMask(ComplianceProfile profile, PiiMaskService mask) {
        return new JdbcAuditSearchService(jdbc, props, profile, mask);
    }

    @Test
    void empty_table_returns_zero_total_and_empty_results() {
        Map<String, Object> r = svc(DefaultProfile.INSTANCE)
            .search(null, null, null, null, null, null, "2026-06-01", "2026-06-30", null, null);

        assertThat(r.get("total")).isEqualTo(0L);
        assertThat(r.get("limit")).isEqualTo(100);
        assertThat(r.get("offset")).isEqualTo(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) r.get("results");
        assertThat(results).isEmpty();
    }

    @Test
    void q_filter_matches_prompt_or_response_case_insensitive() {
        Instant base = Instant.parse("2026-06-15T10:00:00Z");
        insert(base, "alpha", "u1", "openai", "gpt-4o", "대출 한도 조회 부탁드립니다", "한도는 3000만원", "SUCCESS");
        insert(base.plusSeconds(1), "alpha", "u2", "openai", "gpt-4o", "Weather today?", "Sunny in Seoul", "SUCCESS");
        insert(base.plusSeconds(2), "alpha", "u3", "openai", "gpt-4o", "주식 시세", "관련 없는 응답", "SUCCESS");

        Map<String, Object> r = svc(DefaultProfile.INSTANCE)
            .search("대출", null, null, null, null, null, "2026-06-01", "2026-06-30", null, null);
        assertThat(r.get("total")).isEqualTo(1L);

        Map<String, Object> r2 = svc(DefaultProfile.INSTANCE)
            .search("SEOUL", null, null, null, null, null, "2026-06-01", "2026-06-30", null, null);
        assertThat(r2.get("total")).isEqualTo(1L);
    }

    @Test
    void user_team_provider_model_status_filters_combine() {
        Instant base = Instant.parse("2026-06-15T10:00:00Z");
        insert(base, "alpha", "alice", "openai", "gpt-4o", "p", "r", "SUCCESS");
        insert(base.plusSeconds(1), "alpha", "alice", "openai", "gpt-4o-mini", "p", "r", "SUCCESS");
        insert(base.plusSeconds(2), "alpha", "bob", "openai", "gpt-4o", "p", "r", "SUCCESS");
        insert(base.plusSeconds(3), "beta", "alice", "openai", "gpt-4o", "p", "r", "SUCCESS");
        insert(base.plusSeconds(4), "alpha", "alice", "anthropic", "claude-opus-4-7", "p", "r", "FAILED");

        Map<String, Object> r = svc(DefaultProfile.INSTANCE)
            .search(null, "alice", "alpha", "openai", "gpt-4o", "success", "2026-06-01", "2026-06-30", null, null);
        assertThat(r.get("total")).isEqualTo(1L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) r.get("results");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("modelName")).isEqualTo("gpt-4o");
        assertThat(results.get(0).get("status")).isEqualTo("SUCCESS");
    }

    @Test
    void from_to_window_filters_out_of_range_rows() {
        insert(Instant.parse("2026-05-31T23:59:59Z"), "alpha", "u1", "openai", "gpt-4o", "p", "r", "SUCCESS");
        insert(Instant.parse("2026-06-15T10:00:00Z"), "alpha", "u1", "openai", "gpt-4o", "p", "r", "SUCCESS");
        insert(Instant.parse("2026-07-02T00:00:00Z"), "alpha", "u1", "openai", "gpt-4o", "p", "r", "SUCCESS");

        Map<String, Object> r = svc(DefaultProfile.INSTANCE)
            .search(null, null, null, null, null, null, "2026-06-01", "2026-06-30", null, null);
        assertThat(r.get("total")).isEqualTo(1L);
    }

    @Test
    void limit_offset_paginates_and_orders_desc() {
        Instant base = Instant.parse("2026-06-15T10:00:00Z");
        for (int i = 0; i < 5; i++) {
            insert(base.plusSeconds(i), "alpha", "u" + i, "openai", "gpt-4o", "p" + i, "r", "SUCCESS");
        }
        Map<String, Object> page1 = svc(DefaultProfile.INSTANCE)
            .search(null, null, null, null, null, null, "2026-06-01", "2026-06-30", 2, 0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> r1 = (List<Map<String, Object>>) page1.get("results");
        assertThat(page1.get("total")).isEqualTo(5L);
        assertThat(r1).hasSize(2);
        assertThat(r1.get(0).get("prompt")).isEqualTo("p4");
        assertThat(r1.get(1).get("prompt")).isEqualTo("p3");

        Map<String, Object> page2 = svc(DefaultProfile.INSTANCE)
            .search(null, null, null, null, null, null, "2026-06-01", "2026-06-30", 2, 2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> r2 = (List<Map<String, Object>>) page2.get("results");
        assertThat(r2).hasSize(2);
        assertThat(r2.get(0).get("prompt")).isEqualTo("p2");
        assertThat(r2.get(1).get("prompt")).isEqualTo("p1");
    }

    @Test
    void limit_is_clamped_to_max_search_results() {
        props.getActuator().setMaxSearchResults(3);
        Instant base = Instant.parse("2026-06-15T10:00:00Z");
        for (int i = 0; i < 10; i++) {
            insert(base.plusSeconds(i), "alpha", "u", "openai", "gpt-4o", "p", "r", "SUCCESS");
        }
        Map<String, Object> r = svc(DefaultProfile.INSTANCE)
            .search(null, null, null, null, null, null, "2026-06-01", "2026-06-30", 100, 0);
        assertThat(r.get("limit")).isEqualTo(3);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) r.get("results");
        assertThat(results).hasSize(3);
    }

    @Test
    void kr_financial_profile_masks_prompt_and_response_via_pii_service() {
        Instant base = Instant.parse("2026-06-15T10:00:00Z");
        insert(base, "alpha", "u1", "openai", "gpt-4o",
            "고객 900101-1234567 의 대출 한도 조회",
            "010-1234-5678 로 안내드립니다",
            "SUCCESS");

        Map<String, Object> r = svc(KrFinancialProfile.INSTANCE)
            .search(null, null, null, null, null, null, "2026-06-01", "2026-06-30", null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) r.get("results");
        assertThat(results).hasSize(1);
        String prompt = (String) results.get(0).get("prompt");
        String response = (String) results.get(0).get("response");
        assertThat(prompt).doesNotContain("900101-1234567");
        assertThat(response).doesNotContain("010-1234-5678");
    }

    @Test
    void mask_output_property_delegates_to_pii_service_even_for_default_profile() {
        props.getActuator().getSearch().setMaskOutput(true);
        Instant base = Instant.parse("2026-06-15T10:00:00Z");
        insert(base, "alpha", "u1", "openai", "gpt-4o",
            "이메일 user@example.com 로 보내주세요",
            "확인했습니다",
            "SUCCESS");

        // mask-output=true 일 때 DefaultProfile (piiMaskEnabled=false) 라도 PiiMaskService에 위임 — activeIds 가 비면 원문 유지
        Map<String, Object> r = svc(DefaultProfile.INSTANCE)
            .search(null, null, null, null, null, null, "2026-06-01", "2026-06-30", null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) r.get("results");
        String prompt = (String) results.get(0).get("prompt");
        assertThat(prompt).contains("user@example.com");

        // 외부 starter 가 PiiMaskService 에 활성 detector 를 넣으면 — Default 모드 + mask-output 으로도 마스킹됨
        PiiMaskService activeMask = new PiiMaskService(detectors, List.of("email"));
        Map<String, Object> r2 = svcWithMask(DefaultProfile.INSTANCE, activeMask)
            .search(null, null, null, null, null, null, "2026-06-01", "2026-06-30", null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results2 = (List<Map<String, Object>>) r2.get("results");
        assertThat((String) results2.get(0).get("prompt")).doesNotContain("user@example.com");
    }

    @Test
    void default_profile_without_property_does_not_mask() {
        Instant base = Instant.parse("2026-06-15T10:00:00Z");
        insert(base, "alpha", "u1", "openai", "gpt-4o",
            "사업자번호 123-45-67890 조회",
            "처리완료",
            "SUCCESS");

        Map<String, Object> r = svc(DefaultProfile.INSTANCE)
            .search(null, null, null, null, null, null, "2026-06-01", "2026-06-30", null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) r.get("results");
        assertThat((String) results.get(0).get("prompt")).contains("123-45-67890");
    }

    @Test
    void byTrace_returns_chain_in_chronological_order() {
        String trace = "trace-xyz";
        Instant base = Instant.parse("2026-06-15T10:00:00Z");
        insertWithTrace(base.plusSeconds(20), trace, "alpha", "u1", "openai", "gpt-4o", "third", "r", "SUCCESS");
        insertWithTrace(base, trace, "alpha", "u1", "openai", "gpt-4o", "first", "r", "SUCCESS");
        insertWithTrace(base.plusSeconds(10), trace, "alpha", "u1", "openai", "gpt-4o", "second", "r", "SUCCESS");
        insertWithTrace(base, "other-trace", "alpha", "u1", "openai", "gpt-4o", "noise", "r", "SUCCESS");

        Map<String, Object> r = svc(DefaultProfile.INSTANCE).byTrace(trace);
        assertThat(r.get("traceId")).isEqualTo(trace);
        assertThat(r.get("count")).isEqualTo(3L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) r.get("results");
        assertThat(results).extracting(m -> m.get("prompt")).containsExactly("first", "second", "third");
    }

    @Test
    void byTrace_blank_throws() {
        assertThatThrownBy(() -> svc(DefaultProfile.INSTANCE).byTrace(" "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void byTrace_masks_via_pii_service_when_profile_demands_it() {
        String trace = "trace-mask";
        Instant base = Instant.parse("2026-06-15T10:00:00Z");
        insertWithTrace(base, trace, "alpha", "u1", "openai", "gpt-4o",
            "주민 900101-1234567",
            "전화 010-9999-8888",
            "SUCCESS");

        Map<String, Object> r = svc(KrFinancialProfile.INSTANCE).byTrace(trace);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) r.get("results");
        assertThat((String) results.get(0).get("prompt")).doesNotContain("900101-1234567");
        assertThat((String) results.get(0).get("response")).doesNotContain("010-9999-8888");
    }

    @Test
    void flagRecord_sets_flagged_true_and_returns_payload() {
        Instant base = Instant.parse("2026-06-15T10:00:00Z");
        insert(base, "alpha", "u1", "openai", "gpt-4o", "p", "r", "SUCCESS");
        Long id = jdbc.queryForObject("SELECT id FROM llm_invocation_log LIMIT 1", Long.class);
        assertThat(id).isNotNull();

        Map<String, Object> r = svc(DefaultProfile.INSTANCE)
            .flagRecord(id, "잘못된 법령 인용", "compliance@company.com");

        assertThat(r.get("id")).isEqualTo(id);
        assertThat(r.get("flagged")).isEqualTo(true);
        assertThat(r.get("reason")).isEqualTo("잘못된 법령 인용");
        assertThat(r.get("reporter")).isEqualTo("compliance@company.com");

        Boolean persisted = jdbc.queryForObject(
            "SELECT flagged FROM llm_invocation_log WHERE id = ?", Boolean.class, id);
        assertThat(persisted).isTrue();
    }

    @Test
    void flagRecord_unknown_id_returns_flagged_false() {
        Map<String, Object> r = svc(DefaultProfile.INSTANCE).flagRecord(999_999L, "x", "y");
        assertThat(r.get("flagged")).isEqualTo(false);
    }

    @Test
    void invalid_date_throws() {
        assertThatThrownBy(() -> svc(DefaultProfile.INSTANCE)
            .search(null, null, null, null, null, null, "not-a-date", null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void instant_from_to_accepted() {
        insert(Instant.parse("2026-06-15T10:30:00Z"), "alpha", "u1", "openai", "gpt-4o", "p", "r", "SUCCESS");
        Map<String, Object> r = svc(DefaultProfile.INSTANCE)
            .search(null, null, null, null, null, null,
                "2026-06-15T00:00:00Z", "2026-06-15T23:59:59Z", null, null);
        assertThat(r.get("total")).isEqualTo(1L);
        assertThat(r.get("from")).isEqualTo("2026-06-15T00:00:00Z");
        assertThat(r.get("to")).isEqualTo("2026-06-15T23:59:59Z");
    }

    private void insert(Instant invokedAt, String teamId, String userId,
                        String provider, String model,
                        String prompt, String response, String status) {
        insertWithTrace(invokedAt, UUID.randomUUID().toString(), teamId, userId, provider, model, prompt, response, status);
    }

    private void insertWithTrace(Instant invokedAt, String traceId,
                                 String teamId, String userId,
                                 String provider, String model,
                                 String prompt, String response, String status) {
        jdbc.update("""
            INSERT INTO llm_invocation_log (
                invoked_at, trace_id, model_provider, model_name,
                user_id, team_id, prompt, response,
                token_in, token_out, latency_ms, cost_micro_krw,
                status, compliance_profile
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
            Timestamp.from(invokedAt),
            traceId,
            provider, model,
            userId, teamId, prompt, response,
            10, 20, 100, 1_000_000L,
            status, "default"
        );
    }
}
