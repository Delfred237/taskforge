package dev.taskforge;

import dev.taskforge.execution.SimulatedTaskExecutor;
import dev.taskforge.execution.TaskExecutor;
import dev.taskforge.metrics.InMemoryMetricsRegistry;
import dev.taskforge.metrics.MetricsRegistry;
import dev.taskforge.network.server.TaskServer;
import dev.taskforge.queue.BoundedPriorityTaskQueue;
import dev.taskforge.queue.TaskQueue;
import dev.taskforge.queue.TaskSubmitter;
import dev.taskforge.result.InMemoryTaskResultRepository;
import dev.taskforge.result.TaskResult;
import dev.taskforge.result.TaskResultRepository;
import dev.taskforge.task.InMemoryTaskRepository;
import dev.taskforge.task.Task;
import dev.taskforge.task.TaskRepository;
import dev.taskforge.worker.WorkerPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class TaskForgeSystem {

    private static final Logger log = LoggerFactory.getLogger(TaskForgeSystem.class);

    private final TaskQueue queue;
    private final TaskSubmitter submitter;
    private final TaskRepository taskRepository;
    private final TaskResultRepository resultRepository;
    private final MetricsRegistry metrics;
    private final WorkerPool workerPool;
    private final TaskServer server;

    private volatile boolean started = false;

    public TaskForgeSystem(int port, int workerCount, int queueCapacity) {
        this.queue = new BoundedPriorityTaskQueue(queueCapacity);
        this.metrics = new InMemoryMetricsRegistry();
        this.submitter = new TaskSubmitter(queue, metrics);
        this.taskRepository = new InMemoryTaskRepository();
        this.resultRepository = new InMemoryTaskResultRepository();

        TaskExecutor executor = new SimulatedTaskExecutor();
        this.workerPool = new WorkerPool(workerCount, queue, executor);
        this.server = new TaskServer(port, submitter, taskRepository, resultRepository, metrics);
    }

    public synchronized void start() throws IOException {
        if (started) {
            throw new IllegalStateException("TaskForgeSystem is already started");
        }

        workerPool.start();
        server.startAsync();
        started = true;

        log.info("TaskForgeSystem started");
    }

    public synchronized void shutdown(Duration timeout) {
        if (!started) {
            return;
        }

        Objects.requireNonNull(timeout, "timeout must not be null");

        log.info("TaskForgeSystem initiating graceful shutdown...");

        server.stop();
        submitter.close();

        List<Task> pendingTasks = queue.drain();
        for (Task task : pendingTasks) {
            try {
                task.cancel(Instant.now());
                metrics.increment("tasks.cancelled");
                TaskResult result = TaskResult.failure(
                        task.id(),
                        new RuntimeException("Task cancelled due to system shutdown"),
                        Instant.now()
                );
                resultRepository.save(result);
            } catch (RuntimeException e) {
                // Ignorer si la tâche est déjà dans un état terminal
            }
        }

        try {
            workerPool.shutdown(timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        started = false;
        log.info("TaskForgeSystem shutdown complete");
    }

    public boolean isStarted() {
        return started;
    }

    public TaskSubmitter submitter() {
        return submitter;
    }

    public TaskRepository taskRepository() {
        return taskRepository;
    }

    public TaskResultRepository resultRepository() {
        return resultRepository;
    }

    public TaskServer server() {
        return server;
    }

    public MetricsRegistry metrics() {
        return metrics;
    }
}