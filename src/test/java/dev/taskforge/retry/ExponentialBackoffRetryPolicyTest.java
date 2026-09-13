package dev.taskforge.retry;

import dev.taskforge.task.Task;
import dev.taskforge.task.TaskPriority;
import dev.taskforge.task.TaskType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExponentialBackoffRetryPolicyTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static Task newTask(int maxRetries) {
        return Task.create(
                TaskType.COMPUTATION,
                "payload",
                TaskPriority.NORMAL,
                maxRetries,
                Duration.ofSeconds(5)
        );
    }

    @Test
    void delayShouldIncreaseExponentiallyUntilCap() {
        RetryPolicy policy = new ExponentialBackoffRetryPolicy(
                Duration.ofMillis(100),
                Duration.ofMillis(500)
        );

        Task task = newTask(5);

        assertEquals(Duration.ofMillis(100), policy.delayBeforeRetry(task));

        task.start(NOW);
        task.fail(NOW);
        task.retry(NOW);

        task.start(NOW);
        task.fail(NOW);

        assertEquals(Duration.ofMillis(200), policy.delayBeforeRetry(task));

        task.retry(NOW);
        task.start(NOW);
        task.fail(NOW);

        assertEquals(Duration.ofMillis(400), policy.delayBeforeRetry(task));

        task.retry(NOW);
        task.start(NOW);
        task.fail(NOW);

        assertEquals(Duration.ofMillis(500), policy.delayBeforeRetry(task));
    }
}