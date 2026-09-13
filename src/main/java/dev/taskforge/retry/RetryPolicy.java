package dev.taskforge.retry;

import dev.taskforge.task.Task;

import java.time.Duration;

@FunctionalInterface
public interface RetryPolicy {

    Duration delayBeforeRetry(Task task);
}