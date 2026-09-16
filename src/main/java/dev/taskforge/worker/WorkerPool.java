package dev.taskforge.worker;

import dev.taskforge.execution.TaskExecutor;
import dev.taskforge.execution.TimeoutTaskExecutor;
import dev.taskforge.metrics.InMemoryMetricsRegistry;
import dev.taskforge.metrics.MetricsRegistry;
import dev.taskforge.queue.TaskQueue;
import dev.taskforge.result.InMemoryTaskResultRepository;
import dev.taskforge.result.TaskResultRepository;
import dev.taskforge.retry.ExponentialBackoffRetryPolicy;
import dev.taskforge.retry.RetryPolicy;
import dev.taskforge.retry.TaskRetryScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class WorkerPool {

    private static final Logger log = LoggerFactory.getLogger(WorkerPool.class);

    private final int workerCount;
    private final TaskQueue taskQueue;
    private final TaskExecutor taskExecutor;
    private final TaskResultRepository resultRepository;
    private final MetricsRegistry metrics;

    private final ExecutorService workerExecutor;
    private final ExecutorService executionExecutor;
    private final ScheduledExecutorService retryExecutor;

    private final TaskRetryScheduler retryScheduler;
    private final List<Worker> workers;

    private volatile boolean started;
    private volatile boolean shutdownRequested;

    public WorkerPool(int workerCount, TaskQueue taskQueue, TaskExecutor taskExecutor) {
        this(
                workerCount,
                taskQueue,
                taskExecutor,
                new ExponentialBackoffRetryPolicy(Duration.ofMillis(100), Duration.ofSeconds(1)),
                new InMemoryTaskResultRepository(),
                new InMemoryMetricsRegistry()
        );
    }

    public WorkerPool(int workerCount,
                      TaskQueue taskQueue,
                      TaskExecutor taskExecutor,
                      RetryPolicy retryPolicy) {
        this(
                workerCount,
                taskQueue,
                taskExecutor,
                retryPolicy,
                new InMemoryTaskResultRepository(),
                new InMemoryMetricsRegistry()
        );
    }

    public WorkerPool(int workerCount,
                      TaskQueue taskQueue,
                      TaskExecutor taskExecutor,
                      RetryPolicy retryPolicy,
                      TaskResultRepository resultRepository,
                      MetricsRegistry metrics) {
        if (workerCount <= 0) {
            throw new IllegalArgumentException("workerCount must be greater than 0");
        }

        requireNotNull(taskQueue, "taskQueue must not be null");
        requireNotNull(taskExecutor, "taskExecutor must not be null");
        requireNotNull(retryPolicy, "retryPolicy must not be null");
        requireNotNull(resultRepository, "resultRepository must not be null");
        requireNotNull(metrics, "metrics must not be null");

        this.workerCount = workerCount;
        this.taskQueue = taskQueue;
        this.taskExecutor = taskExecutor;
        this.resultRepository = resultRepository;
        this.metrics = metrics;

        this.workerExecutor = Executors.newFixedThreadPool(workerCount, new WorkerThreadFactory());
        this.executionExecutor = Executors.newCachedThreadPool(new ExecutionThreadFactory());
        this.retryExecutor = Executors.newSingleThreadScheduledExecutor(new RetryThreadFactory());

        this.retryScheduler = new TaskRetryScheduler(retryExecutor, retryPolicy);

        TaskExecutor timeoutTaskExecutor = new TimeoutTaskExecutor(taskExecutor, executionExecutor);

        this.workers = new ArrayList<>(workerCount);

        for (int i = 0; i < workerCount; i++) {
            workers.add(new Worker(
                    "Worker-" + (i + 1),
                    taskQueue,
                    timeoutTaskExecutor,
                    retryScheduler,
                    resultRepository,
                    metrics
            ));
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
            workerExecutor.submit(worker);
        }

        started = true;
        log.info("WorkerPool started with {} workers", workerCount);
    }

    public void shutdown(Duration timeout) throws InterruptedException {
        requireNotNull(timeout, "timeout must not be null");

        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }

        shutdownRequested = true;
        log.info("WorkerPool shutting down...");

        long millis = timeout.toMillis();

        workerExecutor.shutdown();

        if (!workerExecutor.awaitTermination(millis, TimeUnit.MILLISECONDS)) {
            workerExecutor.shutdownNow();
            workerExecutor.awaitTermination(millis, TimeUnit.MILLISECONDS);
        }

        retryExecutor.shutdownNow();
        retryExecutor.awaitTermination(millis, TimeUnit.MILLISECONDS);

        executionExecutor.shutdown();

        if (!executionExecutor.awaitTermination(millis, TimeUnit.MILLISECONDS)) {
            executionExecutor.shutdownNow();
            executionExecutor.awaitTermination(millis, TimeUnit.MILLISECONDS);
        }

        log.info("WorkerPool shut down");
    }

    public boolean isShutdown() {
        return workerExecutor.isShutdown()
                && retryExecutor.isShutdown()
                && executionExecutor.isShutdown();
    }

    public boolean isTerminated() {
        return workerExecutor.isTerminated()
                && retryExecutor.isTerminated()
                && executionExecutor.isTerminated();
    }

    public int workerCount() {
        return workerCount;
    }

    public TaskResultRepository resultRepository() {
        return resultRepository;
    }

    private static void requireNotNull(Object value, String message) {
        Objects.requireNonNull(value, message);
    }

    private static final class ExecutionThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "TaskExecutor-" + counter.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }

    private static final class RetryThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "TaskRetryScheduler-" + counter.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }
}