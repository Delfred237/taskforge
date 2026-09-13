package dev.taskforge.queue;

import dev.taskforge.task.Task;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class TaskSubmitter {

    private final TaskQueue taskQueue;
    private final AtomicLong submittedCount = new AtomicLong();
    private final AtomicLong rejectedCount = new AtomicLong();

    public TaskSubmitter(TaskQueue taskQueue) {
        this.taskQueue = Objects.requireNonNull(taskQueue, "taskQueue must not be null");
    }

    public boolean submit(Task task) {
        Objects.requireNonNull(task, "task must not be null");

        boolean accepted = taskQueue.offer(task);

        if (accepted) {
            submittedCount.incrementAndGet();
        } else {
            rejectedCount.incrementAndGet();
        }

        return accepted;
    }

    public void submitBlocking(Task task) throws InterruptedException {
        Objects.requireNonNull(task, "task must not be null");

        taskQueue.put(task);
        submittedCount.incrementAndGet();
    }

    public long submittedCount() {
        return submittedCount.get();
    }

    public long rejectedCount() {
        return rejectedCount.get();
    }
}