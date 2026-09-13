package dev.taskforge.worker;

import dev.taskforge.execution.TaskExecutor;
import dev.taskforge.queue.TaskQueue;
import dev.taskforge.result.InMemoryTaskResultRepository;
import dev.taskforge.result.TaskResult;
import dev.taskforge.result.TaskResultRepository;
import dev.taskforge.retry.TaskRetryScheduler;
import dev.taskforge.task.Task;

import java.time.Instant;
import java.util.concurrent.TimeoutException;

public final class Worker implements Runnable {

    private final String name;
    private final TaskQueue taskQueue;
    private final TaskExecutor taskExecutor;
    private final TaskRetryScheduler retryScheduler;
    private final TaskResultRepository resultRepository;

    public Worker(String name, TaskQueue taskQueue, TaskExecutor taskExecutor) {
        this(name, taskQueue, taskExecutor, null, new InMemoryTaskResultRepository());
    }

    public Worker(String name,
                  TaskQueue taskQueue,
                  TaskExecutor taskExecutor,
                  TaskRetryScheduler retryScheduler) {
        this(name, taskQueue, taskExecutor, retryScheduler, new InMemoryTaskResultRepository());
    }

    public Worker(String name,
                  TaskQueue taskQueue,
                  TaskExecutor taskExecutor,
                  TaskRetryScheduler retryScheduler,
                  TaskResultRepository resultRepository) {
        requireNotNull(name, "name must not be null");
        requireNotNull(taskQueue, "taskQueue must not be null");
        requireNotNull(taskExecutor, "taskExecutor must not be null");
        requireNotNull(resultRepository, "resultRepository must not be null");

        this.name = name;
        this.taskQueue = taskQueue;
        this.taskExecutor = taskExecutor;
        this.retryScheduler = retryScheduler;
        this.resultRepository = resultRepository;
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
            String output = taskExecutor.executeForResult(task);
            Instant completedAt = Instant.now();

            // 1. Sauvegarde du résultat AVANT de changer le statut
            saveResultQuietly(TaskResult.success(task.id(), output, completedAt));

            try {
                // 2. Changement de statut
                task.complete(completedAt);
            } catch (RuntimeException exception) {
                return true;
            }

            return true;
        } catch (TimeoutException exception) {
            Instant now = Instant.now();

            saveResultQuietly(TaskResult.timeout(task.id(), task.timeout(), now));
            timeOutQuietly(task);
            retryIfPossible(task);

            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            Instant now = Instant.now();

            saveResultQuietly(TaskResult.failure(task.id(), exception, now));
            failQuietly(task);

            return false;
        } catch (Exception exception) {
            Instant now = Instant.now();

            saveResultQuietly(TaskResult.failure(task.id(), exception, now));
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

    private void saveResultQuietly(TaskResult result) {
        try {
            resultRepository.save(result);
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