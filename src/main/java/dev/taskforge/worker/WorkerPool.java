package dev.taskforge.worker;

import dev.taskforge.execution.TaskExecutor;
import dev.taskforge.queue.TaskQueue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class WorkerPool {

    private final int workerCount;
    private final TaskQueue taskQueue;
    private final TaskExecutor taskExecutor;
    private final ExecutorService executorService;
    private final List<Worker> workers;

    private volatile boolean started;
    private volatile boolean shutdownRequested;

    public WorkerPool(int workerCount, TaskQueue taskQueue, TaskExecutor taskExecutor) {
        if (workerCount <= 0) {
            throw new IllegalArgumentException("workerCount must be greater than 0");
        }

        requireNotNull(taskQueue, "taskQueue must not be null");
        requireNotNull(taskExecutor, "taskExecutor must not be null");

        this.workerCount = workerCount;
        this.taskQueue = taskQueue;
        this.taskExecutor = taskExecutor;
        this.executorService = Executors.newFixedThreadPool(workerCount, new WorkerThreadFactory());
        this.workers = new ArrayList<>(workerCount);

        for (int i = 0; i < workerCount; i++) {
            workers.add(new Worker("Worker-" + (i + 1), taskQueue, taskExecutor));
        }
    }

    public synchronized void start() {
        if (shutdownRequested) {
            throw new IllegalStateException("WorkerPool has already been shut down");
        }

        if (started) {
            return;
        }

        for (Worker worker : workers) {
            executorService.submit(worker);
        }

        started = true;
    }

    public void shutdown(Duration timeout) throws InterruptedException {
        requireNotNull(timeout, "timeout must not be null");

        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }

        shutdownRequested = true;

        executorService.shutdown();

        if (!executorService.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            executorService.shutdownNow();
            executorService.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    public boolean isShutdown() {
        return executorService.isShutdown();
    }

    public boolean isTerminated() {
        return executorService.isTerminated();
    }

    public int workerCount() {
        return workerCount;
    }

    private static void requireNotNull(Object value, String message) {
        Objects.requireNonNull(value, message);
    }
}