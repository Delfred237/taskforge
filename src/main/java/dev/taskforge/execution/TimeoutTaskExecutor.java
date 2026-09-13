package dev.taskforge.execution;

import dev.taskforge.task.Task;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class TimeoutTaskExecutor implements TaskExecutor {

    private final TaskExecutor delegate;
    private final ExecutorService executionExecutor;

    public TimeoutTaskExecutor(TaskExecutor delegate, ExecutorService executionExecutor) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.executionExecutor = Objects.requireNonNull(executionExecutor, "executionExecutor must not be null");
    }

    @Override
    public void execute(Task task) throws Exception {
        executeForResult(task);
    }

    @Override
    public String executeForResult(Task task) throws Exception {
        Objects.requireNonNull(task, "task must not be null");

        Callable<String> callable = () -> delegate.executeForResult(task);

        Future<String> future = executionExecutor.submit(callable);

        try {
            return future.get(task.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            future.cancel(true);
            throw exception;
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw exception;
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();

            if (cause instanceof InterruptedException interruptedException) {
                throw interruptedException;
            }

            if (cause instanceof Exception taskException) {
                throw taskException;
            }

            if (cause instanceof Error error) {
                throw error;
            }

            throw new RuntimeException("Unexpected task execution failure", cause);
        }
    }
}