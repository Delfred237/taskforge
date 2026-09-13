package dev.taskforge.task;

import java.util.List;
import java.util.Optional;

public interface TaskRepository {
    void save(Task task);
    Optional<Task> findById(TaskId taskId);
    List<Task> findAll();
    int count();
}