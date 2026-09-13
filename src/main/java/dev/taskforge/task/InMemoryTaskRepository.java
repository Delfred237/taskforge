package dev.taskforge.task;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryTaskRepository implements TaskRepository {

    private final Map<TaskId, Task> tasks = new ConcurrentHashMap<>();

    @Override
    public void save(Task task) {
        Objects.requireNonNull(task, "task must not be null");
        tasks.put(task.id(), task);
    }

    @Override
    public Optional<Task> findById(TaskId taskId) {
        Objects.requireNonNull(taskId, "taskId must not be null");
        return Optional.ofNullable(tasks.get(taskId));
    }

    @Override
    public List<Task> findAll() {
        return List.copyOf(tasks.values());
    }

    @Override
    public int count() {
        return tasks.size();
    }
}