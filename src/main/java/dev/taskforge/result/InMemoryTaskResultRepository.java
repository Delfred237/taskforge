package dev.taskforge.result;

import dev.taskforge.task.TaskId;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryTaskResultRepository implements TaskResultRepository {

    private final Map<TaskId, TaskResult> results = new ConcurrentHashMap<>();

    @Override
    public void save(TaskResult result) {
        Objects.requireNonNull(result, "result must not be null");

        results.put(result.taskId(), result);
    }

    @Override
    public Optional<TaskResult> findByTaskId(TaskId taskId) {
        Objects.requireNonNull(taskId, "taskId must not be null");

        return Optional.ofNullable(results.get(taskId));
    }

    @Override
    public List<TaskResult> findAll() {
        return List.copyOf(results.values());
    }

    @Override
    public int count() {
        return results.size();
    }

    @Override
    public void clear() {
        results.clear();
    }
}