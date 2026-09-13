package dev.taskforge.result;

import dev.taskforge.task.TaskId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryTaskResultRepositoryTest {

    @Test
    void saveShouldStoreResultByTaskId() {
        InMemoryTaskResultRepository repository = new InMemoryTaskResultRepository();

        TaskId taskId = TaskId.generate();
        Instant now = Instant.now();

        TaskResult result = TaskResult.success(taskId, "hello", now);

        repository.save(result);

        Optional<TaskResult> found = repository.findByTaskId(taskId);

        assertTrue(found.isPresent());
        assertEquals("hello", found.get().output());
        assertEquals(1, repository.count());
    }

    @Test
    void saveShouldOverwritePreviousResultForSameTask() {
        InMemoryTaskResultRepository repository = new InMemoryTaskResultRepository();

        TaskId taskId = TaskId.generate();
        Instant now = Instant.now();

        repository.save(TaskResult.failure(taskId, new RuntimeException("boom"), now));
        repository.save(TaskResult.success(taskId, "ok", now));

        Optional<TaskResult> found = repository.findByTaskId(taskId);

        assertTrue(found.isPresent());
        assertTrue(found.get().successful());
        assertEquals("ok", found.get().output());
        assertEquals(1, repository.count());
    }
}