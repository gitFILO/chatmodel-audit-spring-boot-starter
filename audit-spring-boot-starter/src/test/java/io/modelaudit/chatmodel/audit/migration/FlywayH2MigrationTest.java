package io.modelaudit.chatmodel.audit.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayH2MigrationTest {

    private EmbeddedDatabase ds;

    @BeforeEach
    void setUp() {
        // Flyway 마이그레이션 단독 검증: 빈 H2에 Flyway가 V_audit_001 을 적용한 결과만 본다
        ds = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName("audit-flyway-" + UUID.randomUUID())
            .build();

        // V_audit_NNN 네이밍은 기본 prefix "V" 와 충돌 → prefix 를 "V_audit_" 로 두면 version 은 "001" 이 됨
        Flyway flyway = Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration/h2")
            .sqlMigrationPrefix("V_audit_")
            .validateMigrationNaming(true)
            .load();
        flyway.migrate();
    }

    @AfterEach
    void tearDown() {
        ds.shutdown();
    }

    @Test
    void table_llm_invocation_log_exists_after_migration() throws Exception {
        try (Connection c = ds.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            try (ResultSet rs = md.getTables(null, null, "LLM_INVOCATION_LOG", new String[]{"TABLE"})) {
                assertThat(rs.next())
                    .as("llm_invocation_log table must exist after Flyway migration")
                    .isTrue();
            }
        }
    }

    @Test
    void all_five_indexes_exist_after_migration() throws Exception {
        Set<String> expected = Set.of(
            "IDX_LIL_INVOKED_DESC",
            "IDX_LIL_TRACE",
            "IDX_LIL_USER_TIME",
            "IDX_LIL_TEAM_TIME",
            "IDX_LIL_MODEL_TIME"
        );

        Set<String> actual = collectIndexNames("LLM_INVOCATION_LOG");

        assertThat(actual)
            .as("Flyway H2 migration must create all five non-primary indexes from vault 04 §2")
            .containsAll(expected);
    }

    @Test
    void invoked_at_index_targets_invoked_at_column() throws Exception {
        Set<String> cols = collectIndexColumns("LLM_INVOCATION_LOG", "IDX_LIL_INVOKED_DESC");
        assertThat(cols).containsExactly("INVOKED_AT");
    }

    @Test
    void trace_index_targets_trace_id_column() throws Exception {
        Set<String> cols = collectIndexColumns("LLM_INVOCATION_LOG", "IDX_LIL_TRACE");
        assertThat(cols).containsExactly("TRACE_ID");
    }

    @Test
    void user_time_index_targets_user_id_and_invoked_at_columns() throws Exception {
        Set<String> cols = collectIndexColumns("LLM_INVOCATION_LOG", "IDX_LIL_USER_TIME");
        assertThat(cols).containsExactlyInAnyOrder("USER_ID", "INVOKED_AT");
    }

    @Test
    void team_time_index_targets_team_id_and_invoked_at_columns() throws Exception {
        Set<String> cols = collectIndexColumns("LLM_INVOCATION_LOG", "IDX_LIL_TEAM_TIME");
        assertThat(cols).containsExactlyInAnyOrder("TEAM_ID", "INVOKED_AT");
    }

    @Test
    void model_time_index_targets_provider_name_and_invoked_at_columns() throws Exception {
        Set<String> cols = collectIndexColumns("LLM_INVOCATION_LOG", "IDX_LIL_MODEL_TIME");
        assertThat(cols).containsExactlyInAnyOrder("MODEL_PROVIDER", "MODEL_NAME", "INVOKED_AT");
    }

    @Test
    void primary_key_column_id_is_unique_indexed() throws Exception {
        // PK 인덱스는 unique=true 로 필터링 시 노출되어야 함
        try (Connection c = ds.getConnection();
             ResultSet rs = c.getMetaData().getIndexInfo(null, null, "LLM_INVOCATION_LOG", true, false)) {
            boolean found = false;
            while (rs.next()) {
                String col = rs.getString("COLUMN_NAME");
                if ("ID".equalsIgnoreCase(col)) {
                    found = true;
                    break;
                }
            }
            assertThat(found).as("primary key on id column must exist").isTrue();
        }
    }

    private Set<String> collectIndexNames(String table) throws Exception {
        Set<String> names = new HashSet<>();
        try (Connection c = ds.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            try (ResultSet rs = md.getIndexInfo(null, null, table, false, false)) {
                while (rs.next()) {
                    String name = rs.getString("INDEX_NAME");
                    if (name != null) names.add(name);
                }
            }
        }
        return names;
    }

    private Set<String> collectIndexColumns(String table, String indexName) throws Exception {
        Set<String> cols = new HashSet<>();
        try (Connection c = ds.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            try (ResultSet rs = md.getIndexInfo(null, null, table, false, false)) {
                while (rs.next()) {
                    if (indexName.equalsIgnoreCase(rs.getString("INDEX_NAME"))) {
                        String col = rs.getString("COLUMN_NAME");
                        if (col != null) cols.add(col);
                    }
                }
            }
        }
        return cols;
    }
}
