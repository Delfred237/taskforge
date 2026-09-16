package dev.taskforge.worker;

import dev.taskforge.execution.TaskExecutor;
import dev.taskforge.metrics.MetricsRegistry;
import dev.taskforge.queue.TaskQueue;
import dev.taskforge.result.InMemoryTaskResultRepository;
import dev.taskforge.result.TaskResult;
import dev.taskforge.result.TaskResultRepository;
import dev.taskforge.retry.TaskRetryScheduler;
import dev.taskforge.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

public final class Worker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(Worker.class);

    private final String name;
    private final TaskQueue taskQueue;
    private final TaskExecutor taskExecutor;
    private final TaskRetryScheduler retryScheduler;
    private final TaskResultRepository resultRepository;
    private final MetricsRegistry metrics;

    public Worker(String name,
                  TaskQueue taskQueue,
                  TaskExecutor taskExecutor,
                  TaskRetryScheduler retryScheduler,
                  TaskResultRepository resultRepository,
                  MetricsRegistry metrics) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.taskQueue = Objects.requireNonNull(taskQueue, "taskQueue must not be null");
        this.taskExecutor = Objects.requireNonNull(taskExecutor, "taskExecutor must not be null");
        this.resultRepository = Objects.requireNonNull(resultRepository, "resultRepository must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.retryScheduler = retryScheduler;
    }

    @Override
    public void run() {
        log.debug("Worker {} started", name);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Task task = taskQueue.take();
                boolean shouldContinue = process(task);

                if (!shouldContinue) {
                    break;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException exception) {
                log.error("Worker {} unexpected error", name, exception);
            }
        }
        log.debug("Worker {} stopped", name);
    }

    private boolean process(Task task) {
        if (task.isTerminal()) {
            return true;
        }

        try {
            task.start(Instant.now());
        } catch (RuntimeException exception) {
            log.warn("Worker {} could not start task {}: {}", name, task.id(), exception.getMessage());
            return true;
        }

        try {
            String output = taskExecutor.executeForResult(task);
            Instant completedAt = Instant.now();

            saveResultQuietly(TaskResult.success(task.id(), output, completedAt));

            try {
                task.complete(completedAt);
                metrics.increment("tasks.completed");
                log.debug("Task {} completed by worker {}", task.id(), name);
            } catch (RuntimeException exception) {
                log.warn("Task {} could not be marked completed: {}", task.id(), exception.getMessage());
                return true;
            }

            return true;
        } catch (TimeoutException exception) {
            Instant now = Instant.now();

            saveResultQuietly(TaskResult.timeout(task.id(), task.timeout(), now));
            timeOutQuietly(task);
            metrics.increment("tasks.timeout");
            log.warn("Task {} timed out in worker {}", task.id(), name);
            retryIfPossible(task);

            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            Instant now = Instant.now();

            saveResultQuietly(TaskResult.failure(task.id(), exception, now));
            failQuietly(task);
            metrics.increment("tasks.interrupted");
            log.warn("Task {} interrupted in worker {}", task.id(), name);

            return false;
        } catch (Exception exception) {
            Instant now = Instant.now();

            saveResultQuietly(TaskResult.failure(task.id(), exception, now));
            failQuietly(task);
            metrics.increment("tasks.failed");
            log.error("Task {} failed in worker {}: {}", task.id(), name, exception.getMessage());
            retryIfPossible(task);

            return true;
        }
    }

    private void failQuietly(Task task) {
        try {
            task.fail(Instant.now());
        } catch (RuntimeException ignored) {
        }
    }

    private void timeOutQuietly(Task task) {
        try {
            task.timeOut(Instant.now());
        } catch (RuntimeException ignored) {
        }
    }

    private void saveResultQuietly(TaskResult result) {
        try {
            resultRepository.save(result);
        } catch (RuntimeException ignored) {
        }
    }

    private void retryIfPossible(Task task) {
        if (retryScheduler == null) {
            return;
        }

        try {
            boolean scheduled = retryScheduler.scheduleRetry(task, taskQueue);
            if (scheduled) {
                metrics.increment("tasks.retried");
                log.info("Task {} scheduled for retry", task.id());
            }
        } catch (RuntimeException ignored) {
        }
    }

    public String name() {
        return name;
    }
}