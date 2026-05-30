package io.modelaudit.chatmodel.audit.writer;

import io.modelaudit.chatmodel.audit.ComplianceAuditProperties;
import io.modelaudit.chatmodel.audit.core.model.AuditRecord;
import io.modelaudit.chatmodel.audit.metrics.AuditMicrometerMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.time.Duration.ofSeconds;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class AsyncBatchWriterTest {

    private static AuditRecord sample(int i) {
        return new AuditRecord(
            Instant.now(), "trace-" + i, "span-" + i,
            "openai", "gpt-4o", "user-" + i, "team-a",
            "p", "r", 10, 20, 100, 1L,
            AuditRecord.Status.SUCCESS, null, null, null, null, null,
            false, 0, false, false, "default"
        );
    }

    private static ComplianceAuditProperties props(int cap, int batch, long flushMs, String policy) {
        ComplianceAuditProperties p = new ComplianceAuditProperties();
        p.getAsync().setQueueCapacity(cap);
        p.getAsync().setFlushBatchSize(batch);
        p.getAsync().setFlushIntervalMs(flushMs);
        p.getAsync().setOverflowPolicy(policy);
        return p;
    }

    @Test
    void flush_persists_all_records_in_batches() throws Exception {
        JdbcAuditRecordRepository repo = mock(JdbcAuditRecordRepository.class);
        List<AuditRecord> seen = new CopyOnWriteArrayList<>();
        doAnswer(inv -> { seen.addAll(inv.getArgument(0)); return null; })
            .when(repo).batchInsert(anyList());

        AuditMicrometerMetrics metrics = new AuditMicrometerMetrics(new SimpleMeterRegistry());
        AsyncBatchWriter writer = new AsyncBatchWriter(repo, props(1000, 10, 50L, "block"), metrics);
        try {
            for (int i = 0; i < 250; i++) writer.offer(sample(i));
            await().atMost(ofSeconds(5)).untilAsserted(() -> assertThat(seen).hasSize(250));
        } finally {
            writer.destroy();
        }
    }

    @Test
    void drop_policy_increments_drop_counter_when_queue_full() throws Exception {
        JdbcAuditRecordRepository repo = mock(JdbcAuditRecordRepository.class);
        AtomicReference<List<AuditRecord>> latest = new AtomicReference<>(new ArrayList<>());
        doAnswer(inv -> { latest.set(inv.getArgument(0)); Thread.sleep(50); return null; })
            .when(repo).batchInsert(anyList());

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuditMicrometerMetrics metrics = new AuditMicrometerMetrics(registry);
        // queue 2, batch 1000 (never triggers flush), very long tick — forces drop
        AsyncBatchWriter writer = new AsyncBatchWriter(repo, props(2, 1000, 60_000L, "drop"), metrics);
        try {
            for (int i = 0; i < 50; i++) writer.offer(sample(i));
            double drops = metrics.dropCounter("overflow").count();
            assertThat(drops).isGreaterThan(0.0);
        } finally {
            writer.destroy();
        }
    }

    @Test
    void destroy_drains_remaining_queue() throws Exception {
        JdbcAuditRecordRepository repo = mock(JdbcAuditRecordRepository.class);
        AtomicInteger persisted = new AtomicInteger();
        doAnswer(inv -> {
            List<AuditRecord> b = inv.getArgument(0);
            persisted.addAndGet(b.size());
            return null;
        }).when(repo).batchInsert(anyList());

        AuditMicrometerMetrics metrics = new AuditMicrometerMetrics(new SimpleMeterRegistry());
        AsyncBatchWriter writer = new AsyncBatchWriter(repo, props(500, 100, 60_000L, "block"), metrics);
        for (int i = 0; i < 137; i++) writer.offer(sample(i));

        writer.destroy();

        assertThat(persisted.get()).isEqualTo(137);
        assertThat(writer.queueSize()).isZero();
        assertThat(writer.isShuttingDown()).isTrue();
    }

    @Test
    void re_queue_on_failure_then_succeed() throws Exception {
        JdbcAuditRecordRepository repo = mock(JdbcAuditRecordRepository.class);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger persisted = new AtomicInteger();
        doAnswer(inv -> {
            int n = calls.incrementAndGet();
            List<AuditRecord> b = inv.getArgument(0);
            if (n == 1) {
                throw new RuntimeException("first try fails");
            }
            persisted.addAndGet(b.size());
            return null;
        }).when(repo).batchInsert(anyList());

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuditMicrometerMetrics metrics = new AuditMicrometerMetrics(registry);
        AsyncBatchWriter writer = new AsyncBatchWriter(repo, props(1000, 5, 30L, "block"), metrics);
        try {
            for (int i = 0; i < 5; i++) writer.offer(sample(i));
            await().atMost(ofSeconds(5)).untilAsserted(() -> {
                assertThat(persisted.get()).isEqualTo(5);
                assertThat(metrics.flushErrorCounter().count()).isGreaterThanOrEqualTo(1.0);
            });
        } finally {
            writer.destroy();
        }
    }
}
