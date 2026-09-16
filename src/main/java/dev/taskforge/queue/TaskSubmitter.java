package dev.taskforge.queue;

import dev.taskforge.task.Task;

import dev.taskforge.metrics.MetricsRegistry;
import dev.taskforge.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class TaskSubmitter implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TaskSubmitter.class);

    private final TaskQueue taskQueue;
    private final MetricsRegistry metrics;
    private final AtomicLong submittedCount = new AtomicLong();
    private final AtomicLong rejectedCount = new AtomicLong();
    private volatile boolean closed = false;

    public TaskSubmitter(TaskQueue taskQueue, MetricsRegistry metrics) {
        this.taskQueue = Objects.requireNonNull(taskQueue, "taskQueue must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    public boolean submit(Task task) {
        ensureOpen();
        Objects.requireNonNull(task, "task must not be null");

        boolean accepted = taskQueue.offer(task);

        if (accepted) {
            submittedCount.incrementAndGet();
            metrics.increment("tasks.submitted");
            log.debug("Task {} submitted", task.id());
        } else {
            rejectedCount.incrementAndGet();
            metrics.increment("tasks.rejected");
            log.warn("Task {} rejected: queue full", task.id());
        }

        return accepted;
    }

    public void submitBlocking(Task task) throws InterruptedException {
        ensureOpen();
        Objects.requireNonNull(task, "task must not be null");

        taskQueue.put(task);
        submittedCount.incrementAndGet();
        metrics.increment("tasks.submitted");
        log.debug("Task {} submitted (blocking)", task.id());
    }

    public long submittedCount() {
        return submittedCount.get();
    }

    public long rejectedCount() {
        return rejectedCount.get();
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
        log.info("TaskSubmitter closed");
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("TaskSubmitter is closed");
        }
    }
}