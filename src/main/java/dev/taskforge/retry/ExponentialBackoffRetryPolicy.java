package dev.taskforge.retry;

import dev.taskforge.task.Task;

import java.time.Duration;
import java.util.Objects;

public record ExponentialBackoffRetryPolicy(
        Duration initialDelay,
        Duration maxDelay
) implements RetryPolicy {

    public ExponentialBackoffRetryPolicy {
        Objects.requireNonNull(initialDelay, "initialDelay must not be null");
        Objects.requireNonNull(maxDelay, "maxDelay must not be null");

        if (initialDelay.isNegative()) {
            throw new IllegalArgumentException("initialDelay must not be negative");
        }

        if (maxDelay.isNegative()) {
            throw new IllegalArgumentException("maxDelay must not be negative");
        }
    }

    @Override
    public Duration delayBeforeRetry(Task task) {
        int retryCount = task.retryCount();

        int shift = Math.min(retryCount, 20);
        long multiplier = 1L << shift;

        Duration delay;

        try {
            delay = initialDelay.multipliedBy(multiplier);
        } catch (ArithmeticException exception) {
            delay = maxDelay;
        }

        return delay.compareTo(maxDelay) > 0 ? maxDelay : delay;
    }
}