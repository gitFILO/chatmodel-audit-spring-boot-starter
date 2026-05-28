package io.github.gitfilo.chatmodel.audit.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleFunction;

// vault 05 §6 메트릭 풀세트 — C4가 호출하는 최소 표면만 v0.1 진입점. M1이 8종 풀세트로 확장.
public class AuditMicrometerMetrics {

    private final MeterRegistry registry;
    private final Timer flushTimer;
    private final Counter flushErrorCounter;
    private final DistributionSummary flushBatchSizeSummary;
    private final ConcurrentHashMap<String, Counter> dropCounters = new ConcurrentHashMap<>();

    public AuditMicrometerMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.flushTimer = Timer.builder("llm.audit.flush.duration").register(registry);
        this.flushErrorCounter = Counter.builder("llm.audit.flush.errors").register(registry);
        this.flushBatchSizeSummary = DistributionSummary.builder("llm.audit.flush.batch.size").register(registry);
    }

    public Counter dropCounter(String reason) {
        return dropCounters.computeIfAbsent(reason, r ->
            Counter.builder("llm.audit.queue.drops").tag("reason", r).register(registry));
    }

    public Counter flushErrorCounter() {
        return flushErrorCounter;
    }

    public Timer.Sample flushTimerStart() {
        return Timer.start(registry);
    }

    public void flushTimerStop(Timer.Sample sample) {
        if (sample != null) {
            sample.stop(flushTimer);
        }
    }

    public void flushBatchSize(int size) {
        flushBatchSizeSummary.record(size);
    }

    public <T> void registerQueueDepthGauge(T source, ToDoubleFunction<T> sizeFn, ToDoubleFunction<T> capacityFn) {
        Gauge.builder("llm.audit.queue.depth", source, sizeFn).register(registry);
        Gauge.builder("llm.audit.queue.capacity", source, capacityFn).register(registry);
    }

    public MeterRegistry registry() {
        return registry;
    }
}
