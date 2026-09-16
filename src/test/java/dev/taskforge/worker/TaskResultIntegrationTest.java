package dev.taskforge.worker;

import dev.taskforge.execution.TaskExecutor;
import dev.taskforge.metrics.InMemoryMetricsRegistry;
import dev.taskforge.metrics.MetricsRegistry;
import dev.taskforge.queue.BoundedPriorityTaskQueue;
import dev.taskforge.queue.TaskQueue;
import dev.taskforge.result.InMemoryTaskResultRepository;
import dev.taskforge.result.TaskResult;
import dev.taskforge.retry.RetryPolicy;
import dev.taskforge.task.Task;
import dev.taskforge.task.TaskPriority;
import dev.taskforge.task.TaskStatus;
import dev.taskforge.task.TaskType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TaskResultIntegrationTest {

    private static Task newTask(int maxRetries, Duration timeout) {
        return Task.create(
                TaskType.COMPUTATION,
                "payload",
                TaskPriority.NORMAL,
                maxRetries,
                timeout
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

    private static TaskExecutor resultExecutor(String output) {
        return new TaskExecutor() {
            @Override
            public void execute(Task task) throws Exception {
                executeForResult(task);
            }

            @Override
            public String executeForResult(Task task) {
                return output;
            }
        };
    }

    private static TaskExecutor failingExecutor(String message) {
        return new TaskExecutor() {
            @Override
            public void execute(Task task) {
                throw new RuntimeException(message);
            }
        };
    }

    private static TaskExecutor sleepingExecutor(long milliseconds) {
        return new TaskExecutor() {
            @Override
            public void execute(Task task) throws Exception {
                Thread.sleep(milliseconds);
            }
        };
    }

    @Test
    void successfulTaskShouldStoreSuccessResult() throws Exception {
        TaskQueue queue = new BoundedPriorityTaskQueue(10);
        InMemoryTaskResultRepository repository = new InMemoryTaskResultRepository();
        MetricsRegistry metrics = new InMemoryMetricsRegistry();
        RetryPolicy retryPolicy = task -> Duration.ZERO;

        WorkerPool pool = new WorkerPool(
                1,
                queue,
                resultExecutor("hello"),
                retryPolicy,
                repository,
                metrics
        );

        Task task = newTask(0, Duration.ofSeconds(5));

        pool.start();
        queue.put(task);

        awaitStatus(task, TaskStatus.COMPLETED, Duration.ofSeconds(5));

        Optional<TaskResult> found = repository.findByTaskId(task.id());

        assertTrue(found.isPresent());
        assertTrue(found.get().successful());
        assertEquals("hello", found.get().output());

        pool.shutdown(Duration.ofSeconds(1));
        assertTrue(pool.isTerminated());
    }

    @Test
    void failedTaskShouldStoreFailureResult() throws Exception {
        TaskQueue queue = new BoundedPriorityTaskQueue(10);
        InMemoryTaskResultRepository repository = new InMemoryTaskResultRepository();
        MetricsRegistry metrics = new InMemoryMetricsRegistry();
        RetryPolicy retryPolicy = task -> Duration.ZERO;

        WorkerPool pool = new WorkerPool(
                1,
                queue,
                failingExecutor("boom"),
                retryPolicy,
                repository,
                metrics
        );

        Task task = newTask(0, Duration.ofSeconds(5));

        pool.start();
        queue.put(task);

        awaitStatus(task, TaskStatus.FAILED, Duration.ofSeconds(5));

        Optional<TaskResult> found = repository.findByTaskId(task.id());

        assertTrue(found.isPresent());
        assertTrue(!found.get().successful());
        assertTrue(found.get().errorMessage().contains("boom"));
        assertEquals(RuntimeException.class.getName(), found.get().errorType());

        pool.shutdown(Duration.ofSeconds(1));
        assertTrue(pool.isTerminated());
    }

    @Test
    void timedOutTaskShouldStoreTimeoutResult() throws Exception {
        TaskQueue queue = new BoundedPriorityTaskQueue(10);
        InMemoryTaskResultRepository repository = new InMemoryTaskResultRepository();
        MetricsRegistry metrics = new InMemoryMetricsRegistry();
        RetryPolicy retryPolicy = task -> Duration.ZERO;

        WorkerPool pool = new WorkerPool(
                1,
                queue,
                sleepingExecutor(300),
                retryPolicy,
                repository,
                metrics
        );

        Task task = newTask(0, Duration.ofMillis(100));

        pool.start();
        queue.put(task);

        awaitStatus(task, TaskStatus.TIMED_OUT, Duration.ofSeconds(5));

        Optional<TaskResult> found = repository.findByTaskId(task.id());

        assertTrue(found.isPresent());
        assertTrue(!found.get().successful());
        assertEquals(TimeoutException.class.getName(), found.get().errorType());
        assertTrue(found.get().errorMessage().contains("timeout"));

        pool.shutdown(Duration.ofSeconds(1));
        assertTrue(pool.isTerminated());
    }

    @Test
    void retriedTaskShouldEventuallyStoreSuccessResult() throws Exception {
        TaskQueue queue = new BoundedPriorityTaskQueue(10);
        InMemoryTaskResultRepository repository = new InMemoryTaskResultRepository();
        MetricsRegistry metrics = new InMemoryMetricsRegistry();
        RetryPolicy retryPolicy = task -> Duration.ofMillis(10);

        AtomicInteger attempts = new AtomicInteger(0);

        TaskExecutor executor = new TaskExecutor() {
            @Override
            public void execute(Task task) throws Exception {
                executeForResult(task);
            }

            @Override
            public String executeForResult(Task task) {
                if (attempts.incrementAndGet() == 1) {
                    throw new RuntimeException("first attempt fails");
                }

                return "ok-after-retry";
            }
        };

        WorkerPool pool = new WorkerPool(
                1,
                queue,
                executor,
                retryPolicy,
                repository,
                metrics
        );

        Task task = newTask(1, Duration.ofSeconds(5));

        pool.start();
        queue.put(task);

        awaitStatus(task, TaskStatus.COMPLETED, Duration.ofSeconds(5));

        Optional<TaskResult> found = repository.findByTaskId(task.id());

        assertTrue(found.isPresent());
        assertTrue(found.get().successful());
        assertEquals("ok-after-retry", found.get().output());

        pool.shutdown(Duration.ofSeconds(1));
        assertTrue(pool.isTerminated());
    }
}