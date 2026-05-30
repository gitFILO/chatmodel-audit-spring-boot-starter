package io.modelaudit.chatmodel.audit.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AuditMicrometerMetricsTest {

    @Test
    void constructor_registers_flush_and_batch_meters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new AuditMicrometerMetrics(registry);

        assertThat(registry.find("llm.audit.flush.duration").timer()).isNotNull();
        assertThat(registry.find("llm.audit.flush.batch.size").summary()).isNotNull();
        assertThat(registry.find("llm.audit.flush.errors").counter()).isNotNull();
    }

    @Test
    void invocation_counter_tags_provider_model_status_team_and_caches() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuditMicrometerMetrics m = new AuditMicrometerMetrics(registry);

        m.recordInvocation("openai", "gpt-4o", "SUCCESS", "team-a");
        m.recordInvocation("openai", "gpt-4o", "SUCCESS", "team-a");
        m.recordInvocation("openai", "gpt-4o", "FAILED", "team-a");

        Counter ok = registry.find("llm.invocation.count")
            .tag("provider", "openai").tag("model", "gpt-4o")
            .tag("status", "SUCCESS").tag("team", "team-a")
            .counter();
        Counter fail = registry.find("llm.invocation.count")
            .tag("provider", "openai").tag("model", "gpt-4o")
            .tag("status", "FAILED").tag("team", "team-a")
            .counter();
        assertThat(ok).isNotNull();
        assertThat(ok.count()).isEqualTo(2.0);
        assertThat(fail).isNotNull();
        assertThat(fail.count()).isEqualTo(1.0);

        // Same tag combination is cached and returns the same instance
        assertThat(m.invocationCounter("openai", "gpt-4o", "SUCCESS", "team-a"))
            .isSameAs(m.invocationCounter("openai", "gpt-4o", "SUCCESS", "team-a"));
    }

    @Test
    void latency_timer_records_duration_per_provider_model() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuditMicrometerMetrics m = new AuditMicrometerMetrics(registry);

        m.recordLatency("anthropic", "claude-3-5-sonnet", Duration.ofMillis(120));
        m.recordLatency("anthropic", "claude-3-5-sonnet", Duration.ofMillis(80));
        m.recordLatency("anthropic", "claude-3-5-sonnet", null);

        Timer t = registry.find("llm.invocation.latency")
            .tag("provider", "anthropic").tag("model", "claude-3-5-sonnet").timer();
        assertThat(t).isNotNull();
        assertThat(t.count()).isEqualTo(2);
        assertThat(t.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(200.0);
    }

    @Test
    void token_input_output_summaries_are_separate() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuditMicrometerMetrics m = new AuditMicrometerMetrics(registry);

        m.recordTokenInput("openai", "gpt-4o", 100);
        m.recordTokenInput("openai", "gpt-4o", 50);
        m.recordTokenOutput("openai", "gpt-4o", 200);
        m.recordTokenInput("openai", "gpt-4o", 0);
        m.recordTokenInput("openai", "gpt-4o", -5);

        DistributionSummary in = registry.find("llm.invocation.tokens.input")
            .tag("provider", "openai").tag("model", "gpt-4o").summary();
        DistributionSummary out = registry.find("llm.invocation.tokens.output")
            .tag("provider", "openai").tag("model", "gpt-4o").summary();
        assertThat(in).isNotNull();
        assertThat(in.count()).isEqualTo(2);
        assertThat(in.totalAmount()).isEqualTo(150.0);
        assertThat(out).isNotNull();
        assertThat(out.count()).isEqualTo(1);
        assertThat(out.totalAmount()).isEqualTo(200.0);
    }

    @Test
    void cost_krw_micro_converts_to_krw_unit() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuditMicrometerMetrics m = new AuditMicrometerMetrics(registry);

        // 2_500_000 micro-KRW = 2.5 KRW
        m.recordCostKrwMicro("openai", "gpt-4o", "team-a", 2_500_000L);
        m.recordCostKrwMicro("openai", "gpt-4o", "team-a", 500_000L);
        m.recordCostKrwMicro("openai", "gpt-4o", "team-a", 0L);

        Counter c = registry.find("llm.invocation.cost.krw")
            .tag("provider", "openai").tag("model", "gpt-4o").tag("team", "team-a")
            .counter();
        assertThat(c).isNotNull();
        assertThat(c.count()).isEqualTo(3.0);
    }

    @Test
    void drop_counter_tagged_by_reason_and_cached() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuditMicrometerMetrics m = new AuditMicrometerMetrics(registry);

        m.dropCounter("overflow").increment();
        m.dropCounter("overflow").increment(2);
        m.dropCounter("shutdown").increment();

        assertThat(registry.find("llm.audit.queue.drops").tag("reason", "overflow").counter().count())
            .isEqualTo(3.0);
        assertThat(registry.find("llm.audit.queue.drops").tag("reason", "shutdown").counter().count())
            .isEqualTo(1.0);
        assertThat(m.dropCounter("overflow")).isSameAs(m.dropCounter("overflow"));
    }

    @Test
    void flush_timer_records_via_sample() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuditMicrometerMetrics m = new AuditMicrometerMetrics(registry);

        Timer.Sample sample = m.flushTimerStart();
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        m.flushTimerStop(sample);
        m.flushTimerStop(null);

        assertThat(m.flushTimer().count()).isEqualTo(1);
    }

    @Test
    void flush_batch_size_records_distribution() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuditMicrometerMetrics m = new AuditMicrometerMetrics(registry);

        m.flushBatchSize(10);
        m.flushBatchSize(20);
        m.flushBatchSize(30);

        assertThat(m.flushBatchSizeSummary().count()).isEqualTo(3);
        assertThat(m.flushBatchSizeSummary().totalAmount()).isEqualTo(60.0);
    }

    @Test
    void queue_depth_and_capacity_gauges_bound_to_source() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuditMicrometerMetrics m = new AuditMicrometerMetrics(registry);

        AtomicInteger size = new AtomicInteger(0);
        AtomicInteger cap = new AtomicInteger(100);
        m.registerQueueDepthGauge(size, AtomicInteger::doubleValue, x -> cap.doubleValue());
        size.set(42);

        Gauge depth = registry.find("llm.audit.queue.depth").gauge();
        Gauge capacity = registry.find("llm.audit.queue.capacity").gauge();
        assertThat(depth).isNotNull();
        assertThat(depth.value()).isEqualTo(42.0);
        assertThat(capacity).isNotNull();
        assertThat(capacity.value()).isEqualTo(100.0);
    }

    @Test
    void null_or_empty_tag_values_fall_back_to_unknown() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuditMicrometerMetrics m = new AuditMicrometerMetrics(registry);

        m.recordInvocation(null, "", "SUCCESS", null);

        Counter c = registry.find("llm.invocation.count")
            .tag("provider", "unknown").tag("model", "unknown")
            .tag("status", "SUCCESS").tag("team", "unknown")
            .counter();
        assertThat(c).isNotNull();
        assertThat(c.count()).isEqualTo(1.0);
    }

    @Test
    void vault_05_section6_full_set_is_registered_after_first_use() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuditMicrometerMetrics m = new AuditMicrometerMetrics(registry);

        // Activate the 5 invocation meters
        m.recordInvocation("openai", "gpt-4o", "SUCCESS", "t");
        m.recordLatency("openai", "gpt-4o", Duration.ofMillis(1));
        m.recordTokenInput("openai", "gpt-4o", 1);
        m.recordTokenOutput("openai", "gpt-4o", 1);
        m.recordCostKrwMicro("openai", "gpt-4o", "t", 1_000_000L);
        // Activate the 3 audit meters (queue gauges registered separately)
        m.dropCounter("overflow").increment();
        m.flushBatchSize(1);
        Timer.Sample s = m.flushTimerStart();
        m.flushTimerStop(s);
        m.registerQueueDepthGauge(new int[]{0, 0}, a -> a[0], a -> a[1]);

        java.util.Set<String> names = new java.util.HashSet<>();
        for (Meter meter : registry.getMeters()) {
            names.add(meter.getId().getName());
        }
        assertThat(names).contains(
            "llm.invocation.count",
            "llm.invocation.latency",
            "llm.invocation.tokens.input",
            "llm.invocation.tokens.output",
            "llm.invocation.cost.krw",
            "llm.audit.queue.depth",
            "llm.audit.queue.capacity",
            "llm.audit.queue.drops",
            "llm.audit.flush.duration",
            "llm.audit.flush.batch.size"
        );
    }
}
