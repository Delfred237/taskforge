package dev.taskforge.task;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private Task newTask(int maxRetries) {
        return Task.create(
                TaskType.COMPUTATION,
                "{}",
                TaskPriority.NORMAL,
                maxRetries,
                TIMEOUT,
                NOW
        );
    }

    @Test
    void createShouldInitializeTaskAsPending() {
        Task task = newTask(0);

        assertNotNull(task.id());
        assertEquals(TaskStatus.PENDING, task.status());
        assertEquals(TaskType.COMPUTATION, task.type());
        assertEquals("{}", task.payload());
        assertEquals(TaskPriority.NORMAL, task.priority());
        assertEquals(NOW, task.createdAt());
        assertEquals(0, task.retryCount());
        assertEquals(0, task.maxRetries());
        assertEquals(TIMEOUT, task.timeout());
        assertNull(task.startedAt());
        assertNull(task.completedAt());
        assertFalse(task.isTerminal());
    }

    @Test
    void createShouldRejectNullType() {
        assertThrows(TaskValidationException.class, () ->
                Task.create(null, "{}", TaskPriority.NORMAL, 0, TIMEOUT, NOW)
        );
    }

    @Test
    void createShouldRejectNullPayload() {
        assertThrows(TaskValidationException.class, () ->
                Task.create(TaskType.COMPUTATION, null, TaskPriority.NORMAL, 0, TIMEOUT, NOW)
        );
    }

    @Test
    void createShouldRejectNullPriority() {
        assertThrows(TaskValidationException.class, () ->
                Task.create(TaskType.COMPUTATION, "{}", null, 0, TIMEOUT, NOW)
        );
    }

    @Test
    void createShouldRejectNegativeMaxRetries() {
        assertThrows(TaskValidationException.class, () ->
                Task.create(TaskType.COMPUTATION, "{}", TaskPriority.NORMAL, -1, TIMEOUT, NOW)
        );
    }

    @Test
    void createShouldRejectZeroTimeout() {
        assertThrows(TaskValidationException.class, () ->
                Task.create(TaskType.COMPUTATION, "{}", TaskPriority.NORMAL, 0, Duration.ZERO, NOW)
        );
    }

    @Test
    void createShouldRejectNegativeTimeout() {
        assertThrows(TaskValidationException.class, () ->
                Task.create(TaskType.COMPUTATION, "{}", TaskPriority.NORMAL, 0, Duration.ofSeconds(-1), NOW)
        );
    }

    @Test
    void startShouldMovePendingToRunning() {
        Task task = newTask(0);

        task.start(NOW);

        assertEquals(TaskStatus.RUNNING, task.status());
        assertEquals(NOW, task.startedAt());
        assertNull(task.completedAt());
    }

    @Test
    void pendingTaskCannotBeCompleted() {
        Task task = newTask(0);

        assertThrows(IllegalTaskStateTransitionException.class, () ->
                task.complete(NOW)
        );
    }

    @Test
    void completeShouldMoveRunningToCompleted() {
        Task task = newTask(0);
        Instant completedAt = NOW.plusSeconds(2);

        task.start(NOW);
        task.complete(completedAt);

        assertEquals(TaskStatus.COMPLETED, task.status());
        assertEquals(completedAt, task.completedAt());
        assertTrue(task.isTerminal());
    }

    @Test
    void cancelPendingShouldMoveToCancelled() {
        Task task = newTask(0);

        task.cancel(NOW);

        assertEquals(TaskStatus.CANCELLED, task.status());
        assertEquals(NOW, task.completedAt());
        assertTrue(task.isTerminal());
    }

    @Test
    void runningTaskCanBeCancelled() {
        Task task = newTask(0);

        task.start(NOW);
        task.cancel(NOW.plusSeconds(1));

        assertEquals(TaskStatus.CANCELLED, task.status());
        assertEquals(NOW.plusSeconds(1), task.completedAt());
        assertTrue(task.isTerminal());
    }

    @Test
    void failedTaskWithRetriesRemainingIsNotTerminal() {
        Task task = newTask(2);

        task.start(NOW);
        task.fail(NOW.plusSeconds(1));

        assertEquals(TaskStatus.FAILED, task.status());
        assertTrue(task.hasRetriesRemaining());
        assertFalse(task.isTerminal());
        assertNull(task.completedAt());
    }

    @Test
    void failedTaskWithoutRetriesIsTerminal() {
        Task task = newTask(0);
        Instant failedAt = NOW.plusSeconds(1);

        task.start(NOW);
        task.fail(failedAt);

        assertEquals(TaskStatus.FAILED, task.status());
        assertFalse(task.hasRetriesRemaining());
        assertTrue(task.isTerminal());
        assertEquals(failedAt, task.completedAt());
    }

    @Test
    void failedTaskWithRetriesCanBeCancelled() {
        Task task = newTask(1);

        task.start(NOW);
        task.fail(NOW.plusSeconds(1));
        task.cancel(NOW.plusSeconds(2));

        assertEquals(TaskStatus.CANCELLED, task.status());
        assertTrue(task.isTerminal());
        assertEquals(NOW.plusSeconds(2), task.completedAt());
    }

    @Test
    void failedTaskWithoutRetriesCannotBeCancelled() {
        Task task = newTask(0);

        task.start(NOW);
        task.fail(NOW.plusSeconds(1));

        assertThrows(IllegalTaskStateTransitionException.class, () ->
                task.cancel(NOW.plusSeconds(2))
        );
    }

    @Test
    void retryFlowShouldIncrementRetryCountAndAllowNewExecution() {
        Task task = newTask(1);

        task.start(NOW);
        task.fail(NOW.plusSeconds(1));
        task.retry(NOW.plusSeconds(2));

        assertEquals(TaskStatus.RETRYING, task.status());
        assertEquals(1, task.retryCount());
        assertFalse(task.hasRetriesRemaining());
        assertFalse(task.isTerminal());

        task.start(NOW.plusSeconds(3));
        assertEquals(TaskStatus.RUNNING, task.status());

        task.complete(NOW.plusSeconds(4));
        assertEquals(TaskStatus.COMPLETED, task.status());
        assertTrue(task.isTerminal());
    }

    @Test
    void retryShouldFailWhenNoRetriesRemain() {
        Task task = newTask(0);

        task.start(NOW);
        task.fail(NOW.plusSeconds(1));

        assertThrows(TaskValidationException.class, () ->
                task.retry(NOW.plusSeconds(2))
        );
    }

    @Test
    void timedOutTaskWithRetriesCanRetry() {
        Task task = newTask(1);

        task.start(NOW);
        task.timeOut(NOW.plusSeconds(1));

        assertEquals(TaskStatus.TIMED_OUT, task.status());
        assertFalse(task.isTerminal());

        task.retry(NOW.plusSeconds(2));

        assertEquals(TaskStatus.RETRYING, task.status());
        assertEquals(1, task.retryCount());
    }

    @Test
    void completedTaskCannotBeCancelled() {
        Task task = newTask(0);

        task.start(NOW);
        task.complete(NOW.plusSeconds(1));

        assertThrows(IllegalTaskStateTransitionException.class, () ->
                task.cancel(NOW.plusSeconds(2))
        );
    }
}