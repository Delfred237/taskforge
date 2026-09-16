package dev.taskforge;

import dev.taskforge.execution.SimulatedTaskExecutor;
import dev.taskforge.execution.TaskExecutor;
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

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class TaskForgeSystem {

    private final TaskQueue queue;
    private final TaskSubmitter submitter;
    private final TaskRepository taskRepository;
    private final TaskResultRepository resultRepository;
    private final WorkerPool workerPool;
    private final TaskServer server;

    private volatile boolean started = false;

    public TaskForgeSystem(int port, int workerCount, int queueCapacity) {
        this.queue = new BoundedPriorityTaskQueue(queueCapacity);
        this.submitter = new TaskSubmitter(queue);
        this.taskRepository = new InMemoryTaskRepository();
        this.resultRepository = new InMemoryTaskResultRepository();

        TaskExecutor executor = new SimulatedTaskExecutor();
        this.workerPool = new WorkerPool(workerCount, queue, executor);
        this.server = new TaskServer(port, submitter, taskRepository, resultRepository);
    }

    public synchronized void start() throws IOException {
        if (started) {
            throw new IllegalStateException("TaskForgeSystem is already started");
        }

        workerPool.start();
        server.startAsync();
        started = true;

        System.out.println("[TaskForgeSystem] System started.");
    }

    public synchronized void shutdown(Duration timeout) {
        if (!started) {
            return;
        }

        Objects.requireNonNull(timeout, "timeout must not be null");

        System.out.println("[TaskForgeSystem] Initiating graceful shutdown...");

        // 1. Arrêter le serveur réseau pour ne plus accepter de clients
        server.stop();

        // 2. Refuser les nouvelles soumissions de tâches
        submitter.close();

        // 3. Vider la queue et annuler les tâches en attente
        List<Task> pendingTasks = queue.drain();
        for (Task task : pendingTasks) {
            try {
                task.cancel(Instant.now());
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

        // 4. Arrêter les workers avec le timeout fourni
        try {
            workerPool.shutdown(timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        started = false;
        System.out.println("[TaskForgeSystem] Shutdown complete.");
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
}