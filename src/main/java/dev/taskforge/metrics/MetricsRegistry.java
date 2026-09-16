package dev.taskforge.metrics;

import java.util.Map;

public interface MetricsRegistry {
    void increment(String name);
    void increment(String name, long amount);
    long get(String name);
    Map<String, Long> snapshot();
}