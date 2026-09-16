package dev.taskforge.network;

import dev.taskforge.execution.SimulatedTaskExecutor;
import dev.taskforge.metrics.InMemoryMetricsRegistry;
import dev.taskforge.metrics.MetricsRegistry;
import dev.taskforge.network.client.TaskClient;
import dev.taskforge.network.protocol.Action;
import dev.taskforge.network.protocol.Request;
import dev.taskforge.network.protocol.Response;
import dev.taskforge.network.server.TaskServer;
import dev.taskforge.queue.BoundedPriorityTaskQueue;
import dev.taskforge.queue.TaskQueue;
import dev.taskforge.queue.TaskSubmitter;
import dev.taskforge.result.InMemoryTaskResultRepository;
import dev.taskforge.result.TaskResultRepository;
import dev.taskforge.task.InMemoryTaskRepository;
import dev.taskforge.task.TaskRepository;
import dev.taskforge.worker.WorkerPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiClientServerTest {

    private TaskQueue queue;
    private TaskSubmitter submitter;
    private TaskRepository taskRepository;
    private TaskResultRepository resultRepository;
    private MetricsRegistry metrics;
    private WorkerPool pool;
    private TaskServer server;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        queue = new BoundedPriorityTaskQueue(100);
        metrics = new InMemoryMetricsRegistry();
        submitter = new TaskSubmitter(queue, metrics);
        taskRepository = new InMemoryTaskRepository();
        resultRepository = new InMemoryTaskResultRepository();

        pool = new WorkerPool(2, queue, new SimulatedTaskExecutor());
        pool.start();

        server = new TaskServer(0, submitter, taskRepository, resultRepository, metrics);
        server.startAsync();
        port = server.getPort();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (server != null) server.stop();
        if (pool != null) pool.shutdown(Duration.ofSeconds(2));
    }

    @Test
    void serverShouldHandleMultipleClientsConcurrently() throws Exception {
        int clientCount = 10;
        ExecutorService clientsExecutor = Executors.newFixedThreadPool(clientCount);
        List<Callable<Response>> tasks = new ArrayList<>();

        for (int i = 0; i < clientCount; i++) {
            tasks.add(() -> {
                try (TaskClient client = new TaskClient("localhost", port)) {
                    client.connect();
                    Request req = client.createRequest(Action.SERVER_STATUS, "");
                    return client.send(req);
                }
            });
        }

        long startTime = System.currentTimeMillis();
        List<Future<Response>> futures = clientsExecutor.invokeAll(tasks);
        long endTime = System.currentTimeMillis();

        clientsExecutor.shutdown();
        assertTrue(clientsExecutor.awaitTermination(5, TimeUnit.SECONDS));

        for (Future<Response> future : futures) {
            Response response = future.get();
            assertTrue(response.success(), "Request should succeed");
            assertEquals("Server status", response.message());
        }

        long duration = endTime - startTime;
        assertTrue(duration < 2000, "Concurrent requests should be processed quickly, took: " + duration + "ms");
    }

    @Test
    void serverShouldNotCrashOnSlowOrSilentClient() throws Exception {
        Socket silentClient = new Socket("localhost", port);

        try (TaskClient activeClient = new TaskClient("localhost", port)) {
            activeClient.connect();
            Request req = activeClient.createRequest(Action.SERVER_STATUS, "");
            Response response = activeClient.send(req);

            assertTrue(response.success());
        }

        silentClient.close();
    }
}