package dev.taskforge.metrics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryMetricsRegistry implements MetricsRegistry {

    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    @Override
    public void increment(String name) {
        increment(name, 1);
    }

    @Override
    public void increment(String name, long amount) {
        counters.computeIfAbsent(name, k -> new AtomicLong(0)).addAndGet(amount);
    }

    @Override
    public long get(String name) {
        AtomicLong counter = counters.get(name);
        return counter == null ? 0 : counter.get();
    }

    @Override
    public Map<String, Long> snapshot() {
        Map<String, Long> result = new HashMap<>();
        for (Map.Entry<String, AtomicLong> entry : counters.entrySet()) {
            result.put(entry.getKey(), entry.getValue().get());
        }
        return result;
    }
}