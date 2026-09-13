package dev.taskforge.task;

import java.time.Duration;
import java.time.Instant;

public final class Task {

    private final TaskId id;
    private final TaskType type;
    private final String payload;
    private final TaskPriority priority;
    private final Instant createdAt;
    private final int maxRetries;
    private final Duration timeout;

    private volatile TaskStatus status;
    private volatile Instant startedAt;
    private volatile Instant completedAt;
    private volatile int retryCount;

    private Task(TaskId id,
                 TaskType type,
                 String payload,
                 TaskPriority priority,
                 int maxRetries,
                 Duration timeout,
                 Instant createdAt) {
        requireNotNull(id, "id must not be null");
        validate(type, payload, priority, maxRetries, timeout, createdAt);

        this.id = id;
        this.type = type;
        this.payload = payload;
        this.priority = priority;
        this.maxRetries = maxRetries;
        this.timeout = timeout;
        this.createdAt = createdAt;

        this.status = TaskStatus.PENDING;
        this.startedAt = null;
        this.completedAt = null;
        this.retryCount = 0;
    }

    public static Task create(TaskType type,
                              String payload,
                              TaskPriority priority,
                              int maxRetries,
                              Duration timeout) {
        return create(type, payload, priority, maxRetries, timeout, Instant.now());
    }

    static Task create(TaskType type,
                       String payload,
                       TaskPriority priority,
                       int maxRetries,
                       Duration timeout,
                       Instant createdAt) {
        return new Task(
                TaskId.generate(),
                type,
                payload,
                priority,
                maxRetries,
                timeout,
                createdAt
        );
    }

    public synchronized void start(Instant now) {
        requireNotNull(now, "now must not be null");
        assertTransitionAllowed(TaskStatus.RUNNING);

        this.status = TaskStatus.RUNNING;
        this.startedAt = now;
    }

    public synchronized void complete(Instant now) {
        requireNotNull(now, "now must not be null");
        assertTransitionAllowed(TaskStatus.COMPLETED);

        this.status = TaskStatus.COMPLETED;
        this.completedAt = now;
    }

    public synchronized void fail(Instant now) {
        requireNotNull(now, "now must not be null");
        assertTransitionAllowed(TaskStatus.FAILED);

        this.status = TaskStatus.FAILED;

        if (!hasRetriesRemaining()) {
            this.completedAt = now;
        }
    }

    public synchronized void timeOut(Instant now) {
        requireNotNull(now, "now must not be null");
        assertTransitionAllowed(TaskStatus.TIMED_OUT);

        this.status = TaskStatus.TIMED_OUT;

        if (!hasRetriesRemaining()) {
            this.completedAt = now;
        }
    }

    public synchronized void retry(Instant now) {
        requireNotNull(now, "now must not be null");
        assertTransitionAllowed(TaskStatus.RETRYING);

        if (!hasRetriesRemaining()) {
            throw new TaskValidationException(
                    "Task " + id + " cannot be retried because no retries remain"
            );
        }

        this.status = TaskStatus.RETRYING;
        this.retryCount++;
    }

    public synchronized void cancel(Instant now) {
        requireNotNull(now, "now must not be null");

        if (!isCancellable()) {
            throw new IllegalTaskStateTransitionException(status, TaskStatus.CANCELLED, id);
        }

        this.status = TaskStatus.CANCELLED;
        this.completedAt = now;
    }

    public boolean hasRetriesRemaining() {
        return retryCount < maxRetries;
    }

    public boolean isCancellable() {
        return !isTerminal() && status.canTransitionTo(TaskStatus.CANCELLED);
    }

    public boolean isTerminal() {
        TaskStatus currentStatus = status;

        if (currentStatus.isAlwaysTerminal()) {
            return true;
        }

        return (currentStatus == TaskStatus.FAILED || currentStatus == TaskStatus.TIMED_OUT)
                && !hasRetriesRemaining();
    }

    private void assertTransitionAllowed(TaskStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalTaskStateTransitionException(status, target, id);
        }
    }

    private static void validate(TaskType type,
                                 String payload,
                                 TaskPriority priority,
                                 int maxRetries,
                                 Duration timeout,
                                 Instant createdAt) {
        requireNotNull(type, "type must not be null");
        requireNotNull(payload, "payload must not be null");
        requireNotNull(priority, "priority must not be null");
        requireNotNull(timeout, "timeout must not be null");
        requireNotNull(createdAt, "createdAt must not be null");

        if (maxRetries < 0) {
            throw new TaskValidationException("maxRetries must be >= 0");
        }

        if (timeout.isNegative() || timeout.isZero()) {
            throw new TaskValidationException("timeout must be positive");
        }
    }

    private static void requireNotNull(Object value, String message) {
        if (value == null) {
            throw new TaskValidationException(message);
        }
    }

    public TaskId id() {
        return id;
    }

    public TaskType type() {
        return type;
    }

    public String payload() {
        return payload;
    }

    public TaskPriority priority() {
        return priority;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public Duration timeout() {
        return timeout;
    }

    public TaskStatus status() {
        return status;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public int retryCount() {
        return retryCount;
    }
}