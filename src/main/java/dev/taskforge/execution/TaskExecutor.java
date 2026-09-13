package dev.taskforge.execution;

import dev.taskforge.task.Task;

@FunctionalInterface
public interface TaskExecutor {

    void execute(Task task) throws Exception;
}