package com.surf.tracker.shared.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class MetricsRegistry {

    private final MeterRegistry registry;

    public MetricsRegistry(MeterRegistry registry) {
        this.registry = registry;
    }

    public Counter counter(String name, String... tags) {
        return Counter.builder(name)
                .tags(tags)
                .register(registry);
    }

    public Timer timer(String name, String... tags) {
        return Timer.builder(name)
                .tags(tags)
                .register(registry);
    }

    public void increment(String name, String... tags) {
        counter(name, tags).increment();
    }

    public void record(String counterName, long count, String... tags) {
        counter(counterName, tags).increment(count);
    }

    public <T> T time(String name, java.util.function.Supplier<T> work, String... tags) {
        return timer(name, tags).record(work);
    }
}
