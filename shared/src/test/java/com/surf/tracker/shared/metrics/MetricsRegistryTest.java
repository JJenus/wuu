package com.surf.tracker.shared.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsRegistryTest {

    private SimpleMeterRegistry registry;
    private MetricsRegistry metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new MetricsRegistry(registry);
    }

    @Test
    void increment_incrementsCounter() {
        metrics.increment("test.counter", "tag", "value");
        assertThat(registry.counter("test.counter", "tag", "value").count()).isEqualTo(1.0);
    }

    @Test
    void record_addsToCounter() {
        metrics.record("test.counter", 5L, "type", "batch");
        assertThat(registry.counter("test.counter", "type", "batch").count()).isEqualTo(5.0);
    }

    @Test
    void time_recordsExecutionTime() {
        String result = metrics.time("test.timer", () -> "ok");
        assertThat(result).isEqualTo("ok");
        assertThat(registry.timer("test.timer").count()).isEqualTo(1L);
    }
}
