package dev.taskforge.retry;

import dev.taskforge.queue.TaskQueue;
import dev.taskforge.task.Task;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class TaskRetryScheduler {

    private final ScheduledExecutorService scheduler;
    private final RetryPolicy retryPolicy;

    public TaskRetryScheduler(ScheduledExecutorService scheduler, RetryPolicy retryPolicy) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
    }

    public boolean scheduleRetry(Task task, TaskQueue taskQueue) {
        Objects.requireNonNull(task, "task must not be null");
        Objects.requireNonNull(taskQueue, "taskQueue must not be null");

        if (!task.hasRetriesRemaining()) {
            return false;
        }

        Duration delay = retryPolicy.delayBeforeRetry(task);

        try {
            task.retry(Instant.now());
        } catch (RuntimeException exception) {
            return false;
        }

        long delayMillis = delay == null ? 0L : Math.max(0L, delay.toMillis());

        try {
            scheduler.schedule(
                    () -> requeue(task, taskQueue),
                    delayMillis,
                    TimeUnit.MILLISECONDS
            );

            return true;
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    private void requeue(Task task, TaskQueue taskQueue) {
        try {
            taskQueue.put(task);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}