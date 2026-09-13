package dev.taskforge.result;

import dev.taskforge.task.TaskId;

import java.util.List;
import java.util.Optional;

public interface TaskResultRepository {

    void save(TaskResult result);

    Optional<TaskResult> findByTaskId(TaskId taskId);

    List<TaskResult> findAll();

    int count();

    void clear();
}