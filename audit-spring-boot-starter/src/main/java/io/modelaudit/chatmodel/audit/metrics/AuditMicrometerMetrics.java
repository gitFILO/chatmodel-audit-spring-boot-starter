package io.modelaudit.chatmodel.audit.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleFunction;

// Full set per vault 05 §6 — 5 invocation meters (count/latency/tokens.in/tokens.out/cost.krw) + 5 audit meters (queue.depth/queue.capacity/queue.drops/flush.duration/flush.batch.size)
public class AuditMicrometerMetrics {

    static final String UNKNOWN = "unknown";

    private final MeterRegistry registry;

    private final Timer flushTimer;
    private final Counter flushErrorCounter;
    private final DistributionSummary flushBatchSizeSummary;

    private final ConcurrentHashMap<String, Counter> dropCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> invocationCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> latencyTimers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DistributionSummary> tokenInputSummaries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DistributionSummary> tokenOutputSummaries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> costKrwCounters = new ConcurrentHashMap<>();

    public AuditMicrometerMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.flushTimer = Timer.builder("llm.audit.flush.duration").register(registry);
        this.flushErrorCounter = Counter.builder("llm.audit.flush.errors").register(registry);
        this.flushBatchSizeSummary = DistributionSummary.builder("llm.audit.flush.batch.size").register(registry);
    }

    // ── Invocation meters (vault 05 §6) ────────────────────────────

    public Counter invocationCounter(String provider, String model, String status, String team) {
        String p = nz(provider), m = nz(model), s = nz(status), t = nz(team);
        return invocationCounters.computeIfAbsent(key(p, m, s, t), k ->
            Counter.builder("llm.invocation.count")
                .tag("provider", p).tag("model", m).tag("status", s).tag("team", t)
                .register(registry));
    }

    public void recordInvocation(String provider, String model, String status, String team) {
        invocationCounter(provider, model, status, team).increment();
    }

    public Timer latencyTimer(String provider, String model) {
        String p = nz(provider), m = nz(model);
        return latencyTimers.computeIfAbsent(key(p, m), k ->
            Timer.builder("llm.invocation.latency")
                .tag("provider", p).tag("model", m)
                .register(registry));
    }

    public void recordLatency(String provider, String model, Duration duration) {
        if (duration == null || duration.isNegative()) {
            return;
        }
        latencyTimer(provider, model).record(duration);
    }

    public DistributionSummary tokenInputSummary(String provider, String model) {
        String p = nz(provider), m = nz(model);
        return tokenInputSummaries.computeIfAbsent(key(p, m), k ->
            DistributionSummary.builder("llm.invocation.tokens.input")
                .tag("provider", p).tag("model", m)
                .baseUnit("tokens")
                .register(registry));
    }

    public void recordTokenInput(String provider, String model, long tokens) {
        if (tokens <= 0) {
            return;
        }
        tokenInputSummary(provider, model).record(tokens);
    }

    public DistributionSummary tokenOutputSummary(String provider, String model) {
        String p = nz(provider), m = nz(model);
        return tokenOutputSummaries.computeIfAbsent(key(p, m), k ->
            DistributionSummary.builder("llm.invocation.tokens.output")
                .tag("provider", p).tag("model", m)
                .baseUnit("tokens")
                .register(registry));
    }

    public void recordTokenOutput(String provider, String model, long tokens) {
        if (tokens <= 0) {
            return;
        }
        tokenOutputSummary(provider, model).record(tokens);
    }

    public Counter costKrwCounter(String provider, String model, String team) {
        String p = nz(provider), m = nz(model), t = nz(team);
        return costKrwCounters.computeIfAbsent(key(p, m, t), k ->
            Counter.builder("llm.invocation.cost.krw")
                .tag("provider", p).tag("model", m).tag("team", t)
                .baseUnit("krw")
                .register(registry));
    }

    // Accumulate micro-KRW input (vault 03 §5-5) into KRW-unit counter
    public void recordCostKrwMicro(String provider, String model, String team, long amountMicroKrw) {
        if (amountMicroKrw <= 0) {
            return;
        }
        costKrwCounter(provider, model, team).increment(amountMicroKrw / 1_000_000.0);
    }

    // ── Audit meters (vault 05 §6) ─────────────────────────────────

    public Counter dropCounter(String reason) {
        String r = nz(reason);
        return dropCounters.computeIfAbsent(r, k ->
            Counter.builder("llm.audit.queue.drops").tag("reason", k).register(registry));
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

    public Timer flushTimer() {
        return flushTimer;
    }

    public void flushBatchSize(int size) {
        flushBatchSizeSummary.record(size);
    }

    public DistributionSummary flushBatchSizeSummary() {
        return flushBatchSizeSummary;
    }

    public <T> void registerQueueDepthGauge(T source, ToDoubleFunction<T> sizeFn, ToDoubleFunction<T> capacityFn) {
        Gauge.builder("llm.audit.queue.depth", source, sizeFn).register(registry);
        Gauge.builder("llm.audit.queue.capacity", source, capacityFn).register(registry);
    }

    public MeterRegistry registry() {
        return registry;
    }

    private static String nz(String v) {
        return (v == null || v.isEmpty()) ? UNKNOWN : v;
    }

    private static String key(String... parts) {
        return String.join("|", parts);
    }
}
