package dev.taskforge.concurrency;

import dev.taskforge.execution.TaskExecutor;
import dev.taskforge.queue.BoundedPriorityTaskQueue;
import dev.taskforge.queue.TaskQueue;
import dev.taskforge.queue.TaskSubmitter;
import dev.taskforge.task.Task;
import dev.taskforge.task.TaskId;
import dev.taskforge.task.TaskPriority;
import dev.taskforge.task.TaskType;
import dev.taskforge.worker.WorkerPool;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProducerConsumerTest {

    private static Task newTask(String payload) {
        return Task.create(
                TaskType.COMPUTATION,
                payload,
                TaskPriority.NORMAL,
                0,
                Duration.ofSeconds(5)
        );
    }

    @Test
    void multipleProducersAndWorkersShouldProcessAllTasksExactlyOnce() throws Exception {
        int producerCount = 3;
        int tasksPerProducer = 100;
        int totalTasks = producerCount * tasksPerProducer;

        TaskQueue queue = new BoundedPriorityTaskQueue(10);
        TaskSubmitter submitter = new TaskSubmitter(queue);

        Set<TaskId> submitted = ConcurrentHashMap.newKeySet();
        Set<TaskId> processed = ConcurrentHashMap.newKeySet();
        AtomicInteger duplicates = new AtomicInteger(0);

        CountDownLatch processedLatch = new CountDownLatch(totalTasks);

        TaskExecutor executor = task -> {
            if (!processed.add(task.id())) {
                duplicates.incrementAndGet();
            }

            processedLatch.countDown();
        };

        WorkerPool pool = new WorkerPool(4, queue, executor);
        pool.start();

        ExecutorService producers = Executors.newFixedThreadPool(producerCount);
        List<Future<?>> futures = new ArrayList<>();

        for (int producerIndex = 0; producerIndex < producerCount; producerIndex++) {
            final int index = producerIndex;

            futures.add(producers.submit(() -> {
                for (int taskIndex = 0; taskIndex < tasksPerProducer; taskIndex++) {
                    Task task = newTask("producer-" + index + "-task-" + taskIndex);

                    try {
                        submitter.submitBlocking(task);
                        submitted.add(task.id());
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }));
        }

        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }

        producers.shutdown();
        assertTrue(producers.awaitTermination(1, TimeUnit.SECONDS));

        assertTrue(processedLatch.await(5, TimeUnit.SECONDS));

        assertEquals(0, duplicates.get());
        assertEquals(totalTasks, submitted.size());
        assertEquals(totalTasks, processed.size());
        assertEquals(submitted, processed);
        assertEquals(totalTasks, submitter.submittedCount());
        assertEquals(0, submitter.rejectedCount());
        assertEquals(0, queue.size());

        pool.shutdown(Duration.ofSeconds(1));
        assertTrue(pool.isTerminated());
    }

    @Test
    void failingTasksShouldNotCauseLossOrDuplicateProcessing() throws Exception {
        int totalTasks = 30;

        TaskQueue queue = new BoundedPriorityTaskQueue(5);
        TaskSubmitter submitter = new TaskSubmitter(queue);

        Set<TaskId> processed = ConcurrentHashMap.newKeySet();
        AtomicInteger duplicates = new AtomicInteger(0);
        CountDownLatch processedLatch = new CountDownLatch(totalTasks);

        TaskExecutor executor = task -> {
            if (!processed.add(task.id())) {
                duplicates.incrementAndGet();
            }

            processedLatch.countDown();

            if (task.payload().startsWith("fail")) {
                throw new RuntimeException("Simulated failure");
            }
        };

        WorkerPool pool = new WorkerPool(3, queue, executor);
        pool.start();

        for (int i = 0; i < totalTasks; i++) {
            String payload = i % 2 == 0 ? "fail-" + i : "ok-" + i;
            submitter.submitBlocking(newTask(payload));
        }

        assertTrue(processedLatch.await(5, TimeUnit.SECONDS));

        assertEquals(0, duplicates.get());
        assertEquals(totalTasks, processed.size());
        assertEquals(totalTasks, submitter.submittedCount());
        assertEquals(0, queue.size());

        pool.shutdown(Duration.ofSeconds(1));
        assertTrue(pool.isTerminated());
    }

    @Test
    void submitShouldReturnFalseWhenQueueIsFull() {
        TaskQueue queue = new BoundedPriorityTaskQueue(1);
        TaskSubmitter submitter = new TaskSubmitter(queue);

        assertTrue(submitter.submit(newTask("task-1")));
        assertFalse(submitter.submit(newTask("task-2")));

        assertEquals(1, submitter.submittedCount());
        assertEquals(1, submitter.rejectedCount());
        assertEquals(1, queue.size());
    }
}