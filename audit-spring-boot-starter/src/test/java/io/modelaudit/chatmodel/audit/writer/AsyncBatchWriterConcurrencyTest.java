package io.modelaudit.chatmodel.audit.writer;

import io.modelaudit.chatmodel.audit.ComplianceAuditProperties;
import io.modelaudit.chatmodel.audit.core.model.AuditRecord;
import io.modelaudit.chatmodel.audit.metrics.AuditMicrometerMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class AsyncBatchWriterConcurrencyTest {

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
    void hundred_threads_thousand_invocations_finish_within_30s_and_lossless_after_shutdown() throws Exception {
        JdbcAuditRecordRepository repo = mock(JdbcAuditRecordRepository.class);
        AtomicInteger persisted = new AtomicInteger();
        doAnswer(inv -> {
            List<AuditRecord> batch = inv.getArgument(0);
            persisted.addAndGet(batch.size());
            return null;
        }).when(repo).batchInsert(anyList());

        AuditMicrometerMetrics metrics = new AuditMicrometerMetrics(new SimpleMeterRegistry());
        // BLOCK 정책 + 큐 10_000 — 백프레셔 받되 손실 없음
        AsyncBatchWriter writer = new AsyncBatchWriter(
            repo, props(10_000, 500, 50L, "block"), metrics);

        int threads = 100;
        int callsPerThread = 1000;
        int total = threads * callsPerThread;

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        long t0 = System.nanoTime();
        try {
            for (int t = 0; t < threads; t++) {
                final int base = t * callsPerThread;
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < callsPerThread; i++) {
                            writer.offer(sample(base + i));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            boolean allOffered = done.await(30, TimeUnit.SECONDS);
            assertThat(allOffered).as("100 thread x 1000 offer 가 30초 안에 완료").isTrue();

            long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
            assertThat(elapsedMs).as("offer 페이즈 경과시간(ms)").isLessThan(30_000L);
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            writer.destroy();
        }

        // graceful shutdown 후 큐는 비고 전수 영속 — totalPersisted == 100_000
        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            assertThat(writer.queueSize()).isZero();
            assertThat(persisted.get()).isEqualTo(total);
        });
        assertThat(writer.isShuttingDown()).isTrue();
    }
}
