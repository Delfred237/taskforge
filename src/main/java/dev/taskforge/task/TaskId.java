package dev.taskforge.task;

import java.util.UUID;

public record TaskId(UUID value) {

    public TaskId {
        if (value == null) {
            throw new TaskValidationException("TaskId value must not be null");
        }
    }

    public static TaskId generate() {
        return new TaskId(UUID.randomUUID());
    }

    public static TaskId from(UUID value) {
        return new TaskId(value);
    }

    public static TaskId fromString(String value) {
        if (value == null) {
            throw new TaskValidationException("TaskId string must not be null");
        }

        try {
            return new TaskId(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw new TaskValidationException("Invalid TaskId string: " + value, exception);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}