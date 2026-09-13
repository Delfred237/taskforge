package dev.taskforge.worker;

import dev.taskforge.queue.BoundedPriorityTaskQueue;
import dev.taskforge.queue.TaskQueue;
import dev.taskforge.task.Task;
import dev.taskforge.task.TaskPriority;
import dev.taskforge.task.TaskStatus;
import dev.taskforge.task.TaskType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class WorkerPoolTest {

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
    void constructorShouldRejectInvalidWorkerCount() {
        TaskQueue queue = new BoundedPriorityTaskQueue(10);

        assertThrows(IllegalArgumentException.class, () ->
                new WorkerPool(0, queue, task -> {
                })
        );

        assertThrows(IllegalArgumentException.class, () ->
                new WorkerPool(-1, queue, task -> {
                })
        );
    }

    @Test
    void workerPoolShouldProcessSubmittedTasks() throws Exception {
        int taskCount = 5;

        TaskQueue queue = new BoundedPriorityTaskQueue(10);
        CountDownLatch executed = new CountDownLatch(taskCount);
        List<Task> tasks = new ArrayList<>();

        WorkerPool pool = new WorkerPool(2, queue, task -> executed.countDown());

        for (int i = 0; i < taskCount; i++) {
            Task task = newTask("task-" + i);
            tasks.add(task);
            queue.put(task);
        }

        pool.start();

        assertTrue(executed.await(2, TimeUnit.SECONDS));

        for (Task task : tasks) {
            awaitStatus(task, TaskStatus.COMPLETED, Duration.ofSeconds(1));
        }

        pool.shutdown(Duration.ofSeconds(2));

        assertTrue(pool.isTerminated());
    }

    @Test
    void workerPoolShouldContinueAfterTaskFailure() throws Exception {
        TaskQueue queue = new BoundedPriorityTaskQueue(10);

        Task failingTask = newTask("fail");
        Task successTask = newTask("success");

        CountDownLatch successExecuted = new CountDownLatch(1);

        WorkerPool pool = new WorkerPool(1, queue, task -> {
            if (task == failingTask) {
                throw new RuntimeException("Simulated failure");
            }

            if (task == successTask) {
                successExecuted.countDown();
            }
        });

        pool.start();

        queue.put(failingTask);
        queue.put(successTask);

        assertTrue(successExecuted.await(2, TimeUnit.SECONDS));

        awaitStatus(failingTask, TaskStatus.FAILED, Duration.ofSeconds(1));
        awaitStatus(successTask, TaskStatus.COMPLETED, Duration.ofSeconds(1));

        pool.shutdown(Duration.ofSeconds(2));

        assertTrue(pool.isTerminated());
    }

    @Test
    void workerPoolShouldShutdownIdleWorkers() throws Exception {
        TaskQueue queue = new BoundedPriorityTaskQueue(10);

        WorkerPool pool = new WorkerPool(2, queue, task -> {
        });

        pool.start();
        pool.shutdown(Duration.ofMillis(300));

        assertTrue(pool.isTerminated());
    }
}