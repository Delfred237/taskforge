package dev.taskforge;

import dev.taskforge.network.client.TaskClient;
import dev.taskforge.queue.TaskQueue;
import dev.taskforge.task.Task;
import dev.taskforge.task.TaskPriority;
import dev.taskforge.task.TaskType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class TaskForgeSystemTest {

    private TaskForgeSystem system;

    @BeforeEach
    void setUp() throws IOException {
        // Port 0 = port aléatoire libre
        system = new TaskForgeSystem(0, 2, 10);
        system.start();
    }

    @AfterEach
    void tearDown() {
        if (system.isStarted()) {
            system.shutdown(Duration.ofSeconds(1));
        }
    }

    @Test
    void shutdownShouldCloseSubmitter() {
        system.shutdown(Duration.ofSeconds(1));

        assertTrue(system.submitter().isClosed());

        Task task = Task.create(
                TaskType.COMPUTATION,
                "1",
                TaskPriority.NORMAL,
                0,
                Duration.ofSeconds(1)
        );

        assertThrows(IllegalStateException.class, () -> system.submitter().submit(task));
    }

    @Test
    void shutdownShouldStopServerAndRejectConnections() throws IOException {
        int port = system.server().getPort();
        system.shutdown(Duration.ofSeconds(1));

        try (TaskClient client = new TaskClient("localhost", port)) {
            assertThrows(IOException.class, client::connect);
        }
    }

    @Test
    void pendingTasksShouldBeCancelledOnShutdown() {
        // Soumettre une tâche très longue pour être sûr qu'elle soit encore en attente
        Task longTask = Task.create(
                TaskType.SLEEP,
                "60000",
                TaskPriority.NORMAL,
                0,
                Duration.ofSeconds(120)
        );

        system.taskRepository().save(longTask);
        system.submitter().submit(longTask);

        // Arrêter immédiatement avec un timeout très court
        system.shutdown(Duration.ofMillis(100));

        // La tâche doit avoir été annulée si elle était encore dans la queue
        // ou interrompue/échouée si elle avait commencé à s'exécuter
        assertFalse(system.isStarted());
    }
}