package dev.taskforge.execution;

import dev.taskforge.task.Task;

@FunctionalInterface
public interface TaskExecutor {

    void execute(Task task) throws Exception;

    default String executeForResult(Task task) throws Exception {
        execute(task);
        return null;
    }
}