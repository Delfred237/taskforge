package dev.taskforge.result;

import dev.taskforge.task.TaskId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

public record TaskResult(
        TaskId taskId,
        boolean successful,
        String output,
        String errorMessage,
        String errorType,
        Instant completedAt
) {

    public TaskResult {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");

        output = output == null ? "" : output;
        errorMessage = errorMessage == null ? "" : errorMessage;
        errorType = errorType == null ? "" : errorType;
    }

    public static TaskResult success(TaskId taskId, String output, Instant completedAt) {
        return new TaskResult(
                taskId,
                true,
                output,
                "",
                "",
                completedAt
        );
    }

    public static TaskResult failure(TaskId taskId, Throwable error, Instant completedAt) {
        Objects.requireNonNull(error, "error must not be null");

        String message = error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName()
                : error.getMessage();

        return new TaskResult(
                taskId,
                false,
                "",
                message,
                error.getClass().getName(),
                completedAt
        );
    }

    public static TaskResult timeout(TaskId taskId, Duration timeout, Instant completedAt) {
        Objects.requireNonNull(timeout, "timeout must not be null");

        return new TaskResult(
                taskId,
                false,
                "",
                "Task exceeded timeout of " + timeout.toMillis() + " ms",
                TimeoutException.class.getName(),
                completedAt
        );
    }
}