package io.modelaudit.chatmodel.audit.writer;

import io.modelaudit.chatmodel.audit.ComplianceAuditProperties;
import io.modelaudit.chatmodel.audit.core.model.AuditRecord;
import io.modelaudit.chatmodel.audit.metrics.AuditMicrometerMetrics;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.DisposableBean;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// vault 11 §3-3 — BLOCK/DROP branching + single daemon scheduler + 30s graceful drain
public class AsyncBatchWriter implements DisposableBean {

    private final BlockingQueue<AuditRecord> queue;
    private final JdbcAuditRecordRepository repo;
    private final ScheduledExecutorService scheduler;
    private final int batchSize;
    private final long flushIntervalMs;
    private final OverflowPolicy overflowPolicy;
    private final AuditMicrometerMetrics metrics;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    public AsyncBatchWriter(JdbcAuditRecordRepository repo,
                            ComplianceAuditProperties props,
                            AuditMicrometerMetrics metrics) {
        ComplianceAuditProperties.Async async = props.getAsync();
        this.queue = new ArrayBlockingQueue<>(async.getQueueCapacity());
        this.repo = repo;
        this.batchSize = Math.max(1, async.getFlushBatchSize());
        this.flushIntervalMs = Math.max(1L, async.getFlushIntervalMs());
        this.overflowPolicy = OverflowPolicy.from(async.getOverflowPolicy());
        this.metrics = metrics;
        if (metrics != null) {
            metrics.registerQueueDepthGauge(this, w -> w.queue.size(), w -> w.queueCapacity());
        }
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "compliance-audit-flush");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleAtFixedRate(this::flush, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
    }

    public void offer(AuditRecord record) {
        if (record == null) {
            return;
        }
        switch (overflowPolicy) {
            case BLOCK -> {
                try {
                    queue.put(record);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            case DROP -> {
                if (!queue.offer(record)) {
                    if (metrics != null) {
                        metrics.dropCounter("overflow").increment();
                    }
                    return;
                }
            }
        }
        if (!shuttingDown.get() && queue.size() >= batchSize) {
            try {
                scheduler.execute(this::flush);
            } catch (Exception ignored) {
                // Ignore if scheduler just shut down — the destroy loop drains the queue
            }
        }
    }

    void flush() {
        if (queue.isEmpty()) {
            return;
        }
        List<AuditRecord> batch = new ArrayList<>(batchSize);
        queue.drainTo(batch, batchSize);
        if (batch.isEmpty()) {
            return;
        }
        Timer.Sample sample = metrics != null ? metrics.flushTimerStart() : null;
        try {
            repo.batchInsert(batch);
            if (metrics != null) {
                metrics.flushBatchSize(batch.size());
            }
        } catch (Exception e) {
            if (metrics != null) {
                metrics.flushErrorCounter().increment();
            }
            // v0.1 best-effort re-enqueue — may drop under DROP mode or full queue; spool lands in v0.4.
            for (AuditRecord r : batch) {
                queue.offer(r);
            }
        } finally {
            if (sample != null && metrics != null) {
                metrics.flushTimerStop(sample);
            }
        }
    }

    public int queueSize() {
        return queue.size();
    }

    public int queueCapacity() {
        return queue.remainingCapacity() + queue.size();
    }

    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    @Override
    public void destroy() throws Exception {
        if (!shuttingDown.compareAndSet(false, true)) {
            return;
        }
        scheduler.shutdown();
        if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
            scheduler.shutdownNow();
        }
        while (!queue.isEmpty()) {
            int before = queue.size();
            flush();
            if (queue.size() >= before) {
                // Prevent infinite loop — abort if size does not shrink due to re-enqueue
                break;
            }
        }
    }
}
