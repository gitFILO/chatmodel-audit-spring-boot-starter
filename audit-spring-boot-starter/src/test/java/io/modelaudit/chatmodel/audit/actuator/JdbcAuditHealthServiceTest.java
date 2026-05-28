package io.modelaudit.chatmodel.audit.actuator;

import io.modelaudit.chatmodel.audit.ComplianceAuditProperties;
import io.modelaudit.chatmodel.audit.core.model.AuditRecord;
import io.modelaudit.chatmodel.audit.metrics.AuditMicrometerMetrics;
import io.modelaudit.chatmodel.audit.writer.AsyncBatchWriter;
import io.modelaudit.chatmodel.audit.writer.JdbcAuditRecordRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcAuditHealthServiceTest {

    private EmbeddedDatabase ds;

    @BeforeEach
    void setUp() {
        ds = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName("audit-health-" + UUID.randomUUID())
            .addScript("classpath:db/migration/h2/V_audit_001__create_llm_invocation_log.sql")
            .build();
    }

    @AfterEach
    void tearDown() {
        ds.shutdown();
    }

    private ComplianceAuditProperties props(int cap, int batch, long flushMs) {
        ComplianceAuditProperties p = new ComplianceAuditProperties();
        p.getAsync().setQueueCapacity(cap);
        p.getAsync().setFlushBatchSize(batch);
        p.getAsync().setFlushIntervalMs(flushMs);
        p.getAsync().setOverflowPolicy("block");
        return p;
    }

    private AuditRecord sample(int i) {
        return new AuditRecord(
            Instant.now(), "trace-" + i, "span-" + i,
            "openai", "gpt-4o", "user-" + i, "team-a",
            "p", "r", 10, 20, 100, 1L,
            AuditRecord.Status.SUCCESS, null, null, null, null, null,
            false, 0, false, false, "default"
        );
    }

    @Test
    void idle_writer_reports_up_with_null_last_flush() throws Exception {
        JdbcAuditRecordRepository repo = mock(JdbcAuditRecordRepository.class);
        AuditMicrometerMetrics metrics = new AuditMicrometerMetrics(new SimpleMeterRegistry());
        AsyncBatchWriter writer = new AsyncBatchWriter(repo, props(100, 10, 60_000L), metrics);
        try {
            JdbcAuditHealthService svc = new JdbcAuditHealthService(writer, ds);

            Map<String, Object> health = svc.health();

            assertThat(health.get("status")).isEqualTo("UP");
            Map<?, ?> details = (Map<?, ?>) health.get("details");
            assertThat(details.get("queueDepth")).isEqualTo(0);
            assertThat(details.get("queueCapacity")).isEqualTo(100);
            assertThat(details.get("lastFlushAt")).isNull();
            assertThat(details.get("lastFlushDurationMs")).isNull();
            assertThat(details.get("dbReachable")).isEqualTo(true);
            assertThat(details.get("spoolEnabled")).isEqualTo(false);
            assertThat(details.get("spoolBacklog")).isEqualTo(0L);
        } finally {
            writer.destroy();
        }
    }

    @Test
    void after_flush_reports_last_flush_at_and_duration() throws Exception {
        JdbcAuditRecordRepository repo = mock(JdbcAuditRecordRepository.class);
        List<AuditRecord> seen = new CopyOnWriteArrayList<>();
        doAnswer(inv -> { seen.addAll(inv.getArgument(0)); return null; })
            .when(repo).batchInsert(anyList());

        AuditMicrometerMetrics metrics = new AuditMicrometerMetrics(new SimpleMeterRegistry());
        AsyncBatchWriter writer = new AsyncBatchWriter(repo, props(100, 5, 30L), metrics);
        try {
            for (int i = 0; i < 5; i++) writer.offer(sample(i));
            await().atMost(ofSeconds(5)).untilAsserted(() -> assertThat(seen).hasSize(5));

            JdbcAuditHealthService svc = new JdbcAuditHealthService(writer, ds);
            Map<String, Object> health = svc.health();

            Map<?, ?> details = (Map<?, ?>) health.get("details");
            assertThat(details.get("lastFlushAt")).isInstanceOf(String.class);
            assertThat((String) details.get("lastFlushAt")).isNotBlank();
            assertThat(details.get("lastFlushDurationMs")).isInstanceOf(Long.class);
            assertThat((Long) details.get("lastFlushDurationMs")).isGreaterThanOrEqualTo(0L);
            assertThat(health.get("status")).isEqualTo("UP");
        } finally {
            writer.destroy();
        }
    }

    @Test
    void db_unreachable_reports_down() throws Exception {
        JdbcAuditRecordRepository repo = mock(JdbcAuditRecordRepository.class);
        AuditMicrometerMetrics metrics = new AuditMicrometerMetrics(new SimpleMeterRegistry());
        AsyncBatchWriter writer = new AsyncBatchWriter(repo, props(100, 10, 60_000L), metrics);

        DataSource broken = mock(DataSource.class);
        when(broken.getConnection()).thenThrow(new SQLException("connection refused"));

        try {
            JdbcAuditHealthService svc = new JdbcAuditHealthService(writer, broken);
            Map<String, Object> health = svc.health();

            assertThat(health.get("status")).isEqualTo("DOWN");
            Map<?, ?> details = (Map<?, ?>) health.get("details");
            assertThat(details.get("dbReachable")).isEqualTo(false);
        } finally {
            writer.destroy();
        }
    }

    @Test
    void queue_full_reports_down_even_when_db_reachable() throws Exception {
        JdbcAuditRecordRepository repo = mock(JdbcAuditRecordRepository.class);
        AuditMicrometerMetrics metrics = new AuditMicrometerMetrics(new SimpleMeterRegistry());
        AsyncBatchWriter writer = new AsyncBatchWriter(repo, props(2, 1000, 60_000L), metrics);
        try {
            // 큐 가득 채움 (drop 정책 아니라도 capacity까지만 들어감 — 여기는 block 정책이라 put 블록될 수 있어 offer 직접 사용 불가)
            writer.offer(sample(0));
            writer.offer(sample(1));

            JdbcAuditHealthService svc = new JdbcAuditHealthService(writer, ds);
            Map<String, Object> health = svc.health();

            Map<?, ?> details = (Map<?, ?>) health.get("details");
            assertThat(details.get("queueDepth")).isEqualTo(2);
            assertThat(details.get("queueCapacity")).isEqualTo(2);
            assertThat(health.get("status")).isEqualTo("DOWN");
        } finally {
            writer.destroy();
        }
    }

    @Test
    void datasource_returning_invalid_connection_reports_down() throws Exception {
        JdbcAuditRecordRepository repo = mock(JdbcAuditRecordRepository.class);
        AuditMicrometerMetrics metrics = new AuditMicrometerMetrics(new SimpleMeterRegistry());
        AsyncBatchWriter writer = new AsyncBatchWriter(repo, props(100, 10, 60_000L), metrics);

        DataSource ds2 = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(conn.isValid(1)).thenReturn(false);
        when(ds2.getConnection()).thenReturn(conn);

        try {
            JdbcAuditHealthService svc = new JdbcAuditHealthService(writer, ds2);
            Map<String, Object> health = svc.health();
            Map<?, ?> details = (Map<?, ?>) health.get("details");
            assertThat(details.get("dbReachable")).isEqualTo(false);
            assertThat(health.get("status")).isEqualTo("DOWN");
        } finally {
            writer.destroy();
        }
    }
}
