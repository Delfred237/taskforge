package dev.taskforge.queue;

import dev.taskforge.task.Task;
import dev.taskforge.task.TaskPriority;
import dev.taskforge.task.TaskType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedPriorityTaskQueueTest {

    private static Task task(TaskPriority priority, String payload) {
        return Task.create(
                TaskType.COMPUTATION,
                payload,
                priority,
                0,
                Duration.ofSeconds(5)
        );
    }

    @Test
    void constructorShouldRejectInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedPriorityTaskQueue(0));
        assertThrows(IllegalArgumentException.class, () -> new BoundedPriorityTaskQueue(-1));
    }

    @Test
    void offerShouldInsertTaskWhenQueueHasCapacity() {
        TaskQueue queue = new BoundedPriorityTaskQueue(2);
        Task task = task(TaskPriority.NORMAL, "task-1");

        boolean inserted = queue.offer(task);

        assertTrue(inserted);
        assertEquals(1, queue.size());
        assertEquals(1, queue.remainingCapacity());
        assertFalse(queue.isEmpty());
    }

    @Test
    void offerShouldReturnFalseWhenQueueIsFull() {
        TaskQueue queue = new BoundedPriorityTaskQueue(1);

        assertTrue(queue.offer(task(TaskPriority.NORMAL, "task-1")));
        assertFalse(queue.offer(task(TaskPriority.NORMAL, "task-2")));

        assertEquals(1, queue.size());
        assertEquals(0, queue.remainingCapacity());
    }

    @Test
    void takeShouldReturnHighestPriorityTaskFirst() throws InterruptedException {
        TaskQueue queue = new BoundedPriorityTaskQueue(10);

        Task low = task(TaskPriority.LOW, "low");
        Task critical = task(TaskPriority.CRITICAL, "critical");
        Task normal = task(TaskPriority.NORMAL, "normal");
        Task high = task(TaskPriority.HIGH, "high");

        queue.put(low);
        queue.put(critical);
        queue.put(normal);
        queue.put(high);

        assertSame(critical, queue.take());
        assertSame(high, queue.take());
        assertSame(normal, queue.take());
        assertSame(low, queue.take());
    }

    @Test
    void takeShouldKeepInsertionOrderForSamePriorityTasks() throws InterruptedException {
        TaskQueue queue = new BoundedPriorityTaskQueue(10);

        Task first = task(TaskPriority.NORMAL, "first");
        Task second = task(TaskPriority.NORMAL, "second");
        Task third = task(TaskPriority.NORMAL, "third");

        queue.put(first);
        queue.put(second);
        queue.put(third);

        assertSame(first, queue.take());
        assertSame(second, queue.take());
        assertSame(third, queue.take());
    }

    @Test
    void pollShouldReturnEmptyWhenTimeoutExpires() throws InterruptedException {
        TaskQueue queue = new BoundedPriorityTaskQueue(10);

        Optional<Task> result = queue.poll(Duration.ofMillis(50));

        assertTrue(result.isEmpty());
    }

    @Test
    void pollShouldReturnTaskWhenTaskIsAvailable() throws InterruptedException {
        TaskQueue queue = new BoundedPriorityTaskQueue(10);
        Task task = task(TaskPriority.HIGH, "task-1");

        queue.put(task);

        Optional<Task> result = queue.poll(Duration.ofMillis(50));

        assertTrue(result.isPresent());
        assertSame(task, result.get());
    }

    @Test
    void takeShouldBlockUntilTaskIsAvailable() throws Exception {
        TaskQueue queue = new BoundedPriorityTaskQueue(10);
        Task task = task(TaskPriority.NORMAL, "delayed-task");

        var executor = Executors.newSingleThreadExecutor();

        try {
            var future = executor.submit(queue::take);

            Thread.sleep(100);
            assertFalse(future.isDone());

            queue.put(task);

            Task taken = future.get(1, TimeUnit.SECONDS);

            assertSame(task, taken);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void putShouldBlockWhenQueueIsFullUntilCapacityIsAvailable() throws Exception {
        TaskQueue queue = new BoundedPriorityTaskQueue(1);

        Task first = task(TaskPriority.NORMAL, "first");
        Task second = task(TaskPriority.HIGH, "second");

        queue.put(first);

        CountDownLatch producerStarted = new CountDownLatch(1);
        CountDownLatch producerFinished = new CountDownLatch(1);

        var executor = Executors.newSingleThreadExecutor();

        try {
            executor.submit(() -> {
                try {
                    producerStarted.countDown();
                    queue.put(second);
                    producerFinished.countDown();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });

            assertTrue(producerStarted.await(1, TimeUnit.SECONDS));

            Thread.sleep(100);

            assertEquals(1, queue.size());
            assertEquals(1, producerFinished.getCount());

            Task taken = queue.take();
            assertSame(first, taken);

            assertTrue(producerFinished.await(1, TimeUnit.SECONDS));
            assertEquals(1, queue.size());
            assertSame(second, queue.take());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void queueShouldRejectNullTask() {
        TaskQueue queue = new BoundedPriorityTaskQueue(10);

        assertThrows(NullPointerException.class, () -> queue.offer(null));
        assertThrows(NullPointerException.class, () -> queue.put(null));
    }

    @Test
    void pollShouldRejectNegativeTimeout() {
        TaskQueue queue = new BoundedPriorityTaskQueue(10);

        assertThrows(IllegalArgumentException.class, () ->
                queue.poll(Duration.ofMillis(-1))
        );
    }
}