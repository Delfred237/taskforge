package dev.taskforge.worker;

import dev.taskforge.execution.TaskExecutor;
import dev.taskforge.queue.TaskQueue;
import dev.taskforge.retry.TaskRetryScheduler;
import dev.taskforge.task.Task;

import java.time.Instant;
import java.util.concurrent.TimeoutException;

public final class Worker implements Runnable {

    private final String name;
    private final TaskQueue taskQueue;
    private final TaskExecutor taskExecutor;
    private final TaskRetryScheduler retryScheduler;

    public Worker(String name, TaskQueue taskQueue, TaskExecutor taskExecutor) {
        this(name, taskQueue, taskExecutor, null);
    }

    public Worker(String name,
                  TaskQueue taskQueue,
                  TaskExecutor taskExecutor,
                  TaskRetryScheduler retryScheduler) {
        requireNotNull(name, "name must not be null");
        requireNotNull(taskQueue, "taskQueue must not be null");
        requireNotNull(taskExecutor, "taskExecutor must not be null");

        this.name = name;
        this.taskQueue = taskQueue;
        this.taskExecutor = taskExecutor;
        this.retryScheduler = retryScheduler;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Task task = taskQueue.take();
                boolean shouldContinue = process(task);

                if (!shouldContinue) {
                    break;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException exception) {
                // Plus tard : logging.
                // On continue pour ne pas tuer le worker sur une erreur inattendue.
            }
        }
    }

    private boolean process(Task task) {
        if (task.isTerminal()) {
            return true;
        }

        try {
            task.start(Instant.now());
        } catch (RuntimeException exception) {
            return true;
        }

        try {
            taskExecutor.execute(task);
            task.complete(Instant.now());
            return true;
        } catch (TimeoutException exception) {
            timeOutQuietly(task);
            retryIfPossible(task);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failQuietly(task);
            return false;
        } catch (Exception exception) {
            failQuietly(task);
            retryIfPossible(task);
            return true;
        }
    }

    private void failQuietly(Task task) {
        try {
            task.fail(Instant.now());
        } catch (RuntimeException ignored) {
        }
    }

    private void timeOutQuietly(Task task) {
        try {
            task.timeOut(Instant.now());
        } catch (RuntimeException ignored) {
        }
    }

    private void retryIfPossible(Task task) {
        if (retryScheduler == null) {
            return;
        }

        try {
            retryScheduler.scheduleRetry(task, taskQueue);
        } catch (RuntimeException ignored) {
        }
    }

    private static void requireNotNull(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    public String name() {
        return name;
    }
}