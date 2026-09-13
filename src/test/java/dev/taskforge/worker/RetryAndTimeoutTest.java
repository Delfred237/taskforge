package dev.taskforge.worker;

import dev.taskforge.execution.TaskExecutor;
import dev.taskforge.queue.BoundedPriorityTaskQueue;
import dev.taskforge.queue.TaskQueue;
import dev.taskforge.retry.RetryPolicy;
import dev.taskforge.task.Task;
import dev.taskforge.task.TaskPriority;
import dev.taskforge.task.TaskStatus;
import dev.taskforge.task.TaskType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class RetryAndTimeoutTest {

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

    @Test
    void failedTaskShouldBeRetriedUntilSuccess() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);

        TaskExecutor executor = task -> {
            if (attempts.incrementAndGet() <= 2) {
                throw new RuntimeException("Simulated failure");
            }
        };

        TaskQueue queue = new BoundedPriorityTaskQueue(10);
        RetryPolicy retryPolicy = task -> Duration.ofMillis(10);

        WorkerPool pool = new WorkerPool(1, queue, executor, retryPolicy);

        Task task = newTask(2, Duration.ofSeconds(5));

        pool.start();
        queue.put(task);

        awaitStatus(task, TaskStatus.COMPLETED, Duration.ofSeconds(5));

        assertEquals(3, attempts.get());
        assertEquals(2, task.retryCount());

        pool.shutdown(Duration.ofSeconds(1));
        assertTrue(pool.isTerminated());
    }

    @Test
    void failedTaskWithoutRetriesShouldRemainFailed() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);

        TaskExecutor executor = task -> {
            attempts.incrementAndGet();
            throw new RuntimeException("Simulated failure");
        };

        TaskQueue queue = new BoundedPriorityTaskQueue(10);
        RetryPolicy retryPolicy = task -> Duration.ofMillis(10);

        WorkerPool pool = new WorkerPool(1, queue, executor, retryPolicy);

        Task task = newTask(0, Duration.ofSeconds(5));

        pool.start();
        queue.put(task);

        awaitStatus(task, TaskStatus.FAILED, Duration.ofSeconds(5));

        assertEquals(1, attempts.get());
        assertEquals(0, task.retryCount());
        assertTrue(task.isTerminal());

        pool.shutdown(Duration.ofSeconds(1));
        assertTrue(pool.isTerminated());
    }

    @Test
    void timedOutTaskShouldBeRetriedAndEventuallyComplete() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);

        TaskExecutor executor = task -> {
            if (attempts.incrementAndGet() == 1) {
                Thread.sleep(500);
            }
        };

        TaskQueue queue = new BoundedPriorityTaskQueue(10);
        RetryPolicy retryPolicy = task -> Duration.ofMillis(10);

        WorkerPool pool = new WorkerPool(1, queue, executor, retryPolicy);

        Task task = Task.create(
                TaskType.SLEEP,
                "500",
                TaskPriority.NORMAL,
                1,
                Duration.ofMillis(100)
        );

        pool.start();
        queue.put(task);

        awaitStatus(task, TaskStatus.COMPLETED, Duration.ofSeconds(5));

        assertEquals(2, attempts.get());
        assertEquals(1, task.retryCount());

        pool.shutdown(Duration.ofSeconds(1));
        assertTrue(pool.isTerminated());
    }

    @Test
    void timedOutTaskWithoutRetriesShouldRemainTimedOut() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);

        TaskExecutor executor = task -> {
            attempts.incrementAndGet();
            Thread.sleep(500);
        };

        TaskQueue queue = new BoundedPriorityTaskQueue(10);
        RetryPolicy retryPolicy = task -> Duration.ofMillis(10);

        WorkerPool pool = new WorkerPool(1, queue, executor, retryPolicy);

        Task task = Task.create(
                TaskType.SLEEP,
                "500",
                TaskPriority.NORMAL,
                0,
                Duration.ofMillis(100)
        );

        pool.start();
        queue.put(task);

        awaitStatus(task, TaskStatus.TIMED_OUT, Duration.ofSeconds(5));

        assertEquals(1, attempts.get());
        assertEquals(0, task.retryCount());
        assertTrue(task.isTerminal());

        pool.shutdown(Duration.ofSeconds(1));
        assertTrue(pool.isTerminated());
    }
}