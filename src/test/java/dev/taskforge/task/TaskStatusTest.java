package dev.taskforge.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskStatusTest {

    @Test
    void pendingShouldTransitionToRunningOrCancelled() {
        assertTrue(TaskStatus.PENDING.canTransitionTo(TaskStatus.RUNNING));
        assertTrue(TaskStatus.PENDING.canTransitionTo(TaskStatus.CANCELLED));

        assertFalse(TaskStatus.PENDING.canTransitionTo(TaskStatus.COMPLETED));
        assertFalse(TaskStatus.PENDING.canTransitionTo(TaskStatus.FAILED));
        assertFalse(TaskStatus.PENDING.canTransitionTo(TaskStatus.TIMED_OUT));
    }

    @Test
    void runningShouldTransitionToCompletionFailureTimeoutOrCancellation() {
        assertTrue(TaskStatus.RUNNING.canTransitionTo(TaskStatus.COMPLETED));
        assertTrue(TaskStatus.RUNNING.canTransitionTo(TaskStatus.FAILED));
        assertTrue(TaskStatus.RUNNING.canTransitionTo(TaskStatus.TIMED_OUT));
        assertTrue(TaskStatus.RUNNING.canTransitionTo(TaskStatus.CANCELLED));

        assertFalse(TaskStatus.RUNNING.canTransitionTo(TaskStatus.PENDING));
        assertFalse(TaskStatus.RUNNING.canTransitionTo(TaskStatus.RETRYING));
    }

    @Test
    void failedShouldTransitionToRetryingOrCancelled() {
        assertTrue(TaskStatus.FAILED.canTransitionTo(TaskStatus.RETRYING));
        assertTrue(TaskStatus.FAILED.canTransitionTo(TaskStatus.CANCELLED));

        assertFalse(TaskStatus.FAILED.canTransitionTo(TaskStatus.COMPLETED));
        assertFalse(TaskStatus.FAILED.canTransitionTo(TaskStatus.RUNNING));
    }

    @Test
    void timedOutShouldTransitionToRetryingOrCancelled() {
        assertTrue(TaskStatus.TIMED_OUT.canTransitionTo(TaskStatus.RETRYING));
        assertTrue(TaskStatus.TIMED_OUT.canTransitionTo(TaskStatus.CANCELLED));

        assertFalse(TaskStatus.TIMED_OUT.canTransitionTo(TaskStatus.COMPLETED));
        assertFalse(TaskStatus.TIMED_OUT.canTransitionTo(TaskStatus.RUNNING));
    }

    @Test
    void retryingShouldTransitionToRunningOrCancelled() {
        assertTrue(TaskStatus.RETRYING.canTransitionTo(TaskStatus.RUNNING));
        assertTrue(TaskStatus.RETRYING.canTransitionTo(TaskStatus.CANCELLED));

        assertFalse(TaskStatus.RETRYING.canTransitionTo(TaskStatus.COMPLETED));
        assertFalse(TaskStatus.RETRYING.canTransitionTo(TaskStatus.FAILED));
    }

    @Test
    void terminalStatesShouldNotTransition() {
        assertFalse(TaskStatus.COMPLETED.canTransitionTo(TaskStatus.RUNNING));
        assertFalse(TaskStatus.COMPLETED.canTransitionTo(TaskStatus.PENDING));
        assertFalse(TaskStatus.COMPLETED.canTransitionTo(TaskStatus.CANCELLED));

        assertFalse(TaskStatus.CANCELLED.canTransitionTo(TaskStatus.RUNNING));
        assertFalse(TaskStatus.CANCELLED.canTransitionTo(TaskStatus.PENDING));
        assertFalse(TaskStatus.CANCELLED.canTransitionTo(TaskStatus.COMPLETED));
    }

    @Test
    void alwaysTerminalStatesShouldBeCompletedAndCancelled() {
        assertTrue(TaskStatus.COMPLETED.isAlwaysTerminal());
        assertTrue(TaskStatus.CANCELLED.isAlwaysTerminal());

        assertFalse(TaskStatus.FAILED.isAlwaysTerminal());
        assertFalse(TaskStatus.TIMED_OUT.isAlwaysTerminal());
        assertFalse(TaskStatus.RUNNING.isAlwaysTerminal());
        assertFalse(TaskStatus.PENDING.isAlwaysTerminal());
        assertFalse(TaskStatus.RETRYING.isAlwaysTerminal());
    }
}