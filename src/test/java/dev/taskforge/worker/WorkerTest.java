package dev.taskforge.worker;

import dev.taskforge.execution.TaskExecutor;
import dev.taskforge.metrics.InMemoryMetricsRegistry;
import dev.taskforge.metrics.MetricsRegistry;
import dev.taskforge.queue.BoundedPriorityTaskQueue;
import dev.taskforge.queue.TaskQueue;
import dev.taskforge.result.InMemoryTaskResultRepository;
import dev.taskforge.result.TaskResultRepository;
import dev.taskforge.task.Task;
import dev.taskforge.task.TaskPriority;
import dev.taskforge.task.TaskStatus;
import dev.taskforge.task.TaskType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class WorkerTest {

    private static Task newTask(String payload) {
        return Task.create(
                TaskType.COMPUTATION,
                payload,
                TaskPriority.NORMAL,
                0,
                Duration.ofSeconds(5)
        );
    }

    private static void awaitStatus(Task task, TaskStatus expectedStatus, Duration timeout)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);

        while (Instant.now().isBefore(deadline)) {
            if (task.status() == expectedStatus) {
                return;
            }

            Thread.sleep(10);
        }

        fail("Expected status " + expectedStatus + " but was " + task.status());
    }

    @Test
    void workerShouldExecuteTaskAndMarkCompleted() throws Exception {
        TaskQueue queue = new BoundedPriorityTaskQueue(10);
        MetricsRegistry metrics = new InMemoryMetricsRegistry();
        TaskResultRepository resultRepository = new InMemoryTaskResultRepository();
        Task task = newTask("100");

        TaskExecutor executor = executedTask -> {
            // Simulation d'exécution.
        };

        Worker worker = new Worker("Worker-1", queue, executor, null, resultRepository, metrics);
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        try {
            executorService.submit(worker);
            queue.put(task);

            awaitStatus(task, TaskStatus.COMPLETED, Duration.ofSeconds(1));

            assertEquals(0, queue.size());
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void workerShouldMarkTaskFailedWhenExecutionThrowsException() throws Exception {
        TaskQueue queue = new BoundedPriorityTaskQueue(10);
        MetricsRegistry metrics = new InMemoryMetricsRegistry();
        TaskResultRepository resultRepository = new InMemoryTaskResultRepository();
        Task task = newTask("100");

        TaskExecutor executor = executedTask -> {
            throw new RuntimeException("Simulated failure");
        };

        Worker worker = new Worker("Worker-1", queue, executor, null, resultRepository, metrics);
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        try {
            executorService.submit(worker);
            queue.put(task);

            awaitStatus(task, TaskStatus.FAILED, Duration.ofSeconds(1));

            assertTrue(task.isTerminal());
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void workerShouldContinueProcessingAfterTaskFailure() throws Exception {
        TaskQueue queue = new BoundedPriorityTaskQueue(10);
        MetricsRegistry metrics = new InMemoryMetricsRegistry();
        TaskResultRepository resultRepository = new InMemoryTaskResultRepository();

        Task failingTask = newTask("fail");
        Task successTask = newTask("success");

        TaskExecutor executor = executedTask -> {
            if (executedTask == failingTask) {
                throw new RuntimeException("Simulated failure");
            }
        };

        Worker worker = new Worker("Worker-1", queue, executor, null, resultRepository, metrics);
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        try {
            executorService.submit(worker);

            queue.put(failingTask);
            queue.put(successTask);

            awaitStatus(failingTask, TaskStatus.FAILED, Duration.ofSeconds(1));
            awaitStatus(successTask, TaskStatus.COMPLETED, Duration.ofSeconds(1));
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void workerShouldStopWhenInterrupted() throws Exception {
        TaskQueue queue = new BoundedPriorityTaskQueue(10);
        MetricsRegistry metrics = new InMemoryMetricsRegistry();
        TaskResultRepository resultRepository = new InMemoryTaskResultRepository();

        Worker worker = new Worker("Worker-1", queue, executedTask -> {
        }, null, resultRepository, metrics);

        ExecutorService executorService = Executors.newSingleThreadExecutor();

        executorService.submit(worker);

        Thread.sleep(50);

        executorService.shutdownNow();

        assertTrue(executorService.awaitTermination(1, TimeUnit.SECONDS));
    }
}