package io.modelaudit.chatmodel.audit.writer;

import io.modelaudit.chatmodel.audit.ComplianceAuditProperties;
import io.modelaudit.chatmodel.audit.core.model.AuditRecord;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

public class JdbcAuditRecordRepository {

    // vault 11 §3-4 INSERT_SQL — canonical form with PG JSONB cast (cast stripped for H2)
    static final String INSERT_SQL = """
        INSERT INTO %s (
            invoked_at, trace_id, span_id,
            model_provider, model_name, user_id, team_id,
            prompt, response, token_in, token_out, latency_ms, cost_micro_krw,
            status, error_class, error_message, finish_reason,
            tool_calls_json, metadata_json,
            pii_masked, masked_pii_count, external_sent, flagged,
            compliance_profile
        ) VALUES (?,?,?, ?,?,?,?, ?,?,?,?,?,?, ?,?,?,?, ?::jsonb,?::jsonb, ?,?,?,?, ?)
        """;

    private final JdbcTemplate jdbc;
    private final String sql;

    public JdbcAuditRecordRepository(DataSource ds, ComplianceAuditProperties props) {
        this.jdbc = new JdbcTemplate(ds);
        String table = (props.getSchema() != null ? props.getSchema() + "." : "") + props.getTableName();
        // PG keeps ?::jsonb cast; H2 uses JSON column so cast is stripped
        String template = isPostgres(ds) ? INSERT_SQL : INSERT_SQL.replace("?::jsonb", "?");
        this.sql = String.format(template, table);
    }

    public void batchInsert(List<AuditRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                AuditRecord r = records.get(i);
                int idx = 0;
                ps.setTimestamp(++idx, Timestamp.from(r.invokedAt()));
                ps.setString(++idx, r.traceId());
                ps.setString(++idx, r.spanId());
                ps.setString(++idx, r.modelProvider());
                ps.setString(++idx, r.modelName());
                ps.setString(++idx, r.userId());
                ps.setString(++idx, r.teamId());
                ps.setString(++idx, r.prompt());
                ps.setString(++idx, r.response());
                setNullableInt(ps, ++idx, r.tokenIn());
                setNullableInt(ps, ++idx, r.tokenOut());
                ps.setInt(++idx, r.latencyMs());
                setNullableLong(ps, ++idx, r.costMicroKrw());
                ps.setString(++idx, r.status().name());
                ps.setString(++idx, r.errorClass());
                ps.setString(++idx, r.errorMessage());
                ps.setString(++idx, r.finishReason());
                ps.setString(++idx, r.toolCallsJson());
                ps.setString(++idx, r.metadataJson());
                ps.setBoolean(++idx, r.piiMasked());
                ps.setInt(++idx, r.maskedPiiCount());
                ps.setBoolean(++idx, r.externalSent());
                ps.setBoolean(++idx, r.flagged());
                ps.setString(++idx, r.complianceProfile());
            }

            @Override
            public int getBatchSize() {
                return records.size();
            }
        });
    }

    private static boolean isPostgres(DataSource ds) {
        try (Connection conn = ds.getConnection()) {
            String name = conn.getMetaData().getDatabaseProductName();
            return name != null && name.toLowerCase().contains("postgres");
        } catch (SQLException e) {
            return false;
        }
    }

    private static void setNullableInt(PreparedStatement ps, int idx, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, value);
        }
    }

    private static void setNullableLong(PreparedStatement ps, int idx, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.BIGINT);
        } else {
            ps.setLong(idx, value);
        }
    }
}
