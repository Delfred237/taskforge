package dev.taskforge;

import dev.taskforge.network.client.TaskClient;
import dev.taskforge.network.protocol.Action;
import dev.taskforge.network.protocol.MessageCodec;
import dev.taskforge.network.protocol.Request;
import dev.taskforge.network.protocol.Response;
import dev.taskforge.result.TaskResult;
import dev.taskforge.task.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskForgeSystemIntegrationTest {

    private TaskForgeSystem system;
    private int port;
    private MessageCodec codec;

    @BeforeEach
    void setUp() throws IOException {
        codec = new MessageCodec();
        // Port 0 pour laisser l'OS choisir un port libre
        system = new TaskForgeSystem(0, 2, 100);
        system.start();
        port = system.server().getPort();
    }

    @AfterEach
    void tearDown() {
        if (system != null && system.isStarted()) {
            system.shutdown(Duration.ofSeconds(2));
        }
    }

    @Test
    void fullLifecycle_ShouldSubmitProcessAndRetrieveResult() throws Exception {
        try (TaskClient client = new TaskClient("localhost", port)) {
            client.connect();

            // 1. Soumettre une tâche rapide (COMPUTATION)
            String submitPayload = codec.encode(Map.of(
                    "type", "COMPUTATION",
                    "data", "100",
                    "priority", "HIGH",
                    "maxRetries", 0,
                    "timeoutMs", 5000
            ));

            Request submitReq = client.createRequest(Action.SUBMIT_TASK, submitPayload);
            Response submitResp = client.send(submitReq);

            assertTrue(submitResp.success(), "Submit should succeed");
            String taskId = submitResp.payload();
            assertNotNull(taskId, "Task ID should be returned");

            // 2. Poller le statut jusqu'à ce qu'il soit terminal
            TaskStatus finalStatus = null;
            long deadline = System.currentTimeMillis() + 5000;

            while (System.currentTimeMillis() < deadline) {
                Request statusReq = client.createRequest(Action.GET_TASK_STATUS, taskId);
                Response statusResp = client.send(statusReq);

                assertTrue(statusResp.success());
                String statusStr = statusResp.payload();

                if (statusStr.equals(TaskStatus.COMPLETED.name()) ||
                        statusStr.equals(TaskStatus.FAILED.name()) ||
                        statusStr.equals(TaskStatus.CANCELLED.name()) ||
                        statusStr.equals(TaskStatus.TIMED_OUT.name())) {
                    finalStatus = TaskStatus.valueOf(statusStr);
                    break;
                }

                Thread.sleep(50);
            }

            assertEquals(TaskStatus.COMPLETED, finalStatus, "Task should complete successfully");

            // 3. Récupérer le résultat
            Request resultReq = client.createRequest(Action.GET_TASK_RESULT, taskId);
            Response resultResp = client.send(resultReq);

            assertTrue(resultResp.success(), "Result retrieval should succeed");

            // Le payload du résultat est lui-même du JSON.
            // On le désérialise avec le MÊME codec que le serveur
            String resultJson = resultResp.payload();
            assertNotNull(resultJson);

            TaskResult parsedResult = codec.decode(resultJson, TaskResult.class);
            assertNotNull(parsedResult, "Parsed result should not be null");
            assertTrue(parsedResult.successful(), "Result should be marked as successful");
            assertTrue(parsedResult.output().contains("iterations=100"), "Output should contain computation result");

            // 4. Vérifier les métriques via SERVER_STATUS
            Request metricsReq = client.createRequest(Action.SERVER_STATUS, "");
            Response metricsResp = client.send(metricsReq);

            assertTrue(metricsResp.success());
            assertTrue(metricsResp.payload().contains("\"tasks.submitted\""));
            assertTrue(metricsResp.payload().contains("\"tasks.completed\""));
        }
    }

    @Test
    void getTaskStatus_ShouldFailForUnknownTask() throws Exception {
        try (TaskClient client = new TaskClient("localhost", port)) {
            client.connect();

            String fakeId = "00000000-0000-0000-0000-000000000000";
            Request req = client.createRequest(Action.GET_TASK_STATUS, fakeId);
            Response resp = client.send(req);

            assertFalse(resp.success(), "Should fail for unknown task");
            assertTrue(resp.message().contains("not found"));
        }
    }

    @Test
    void getTaskResult_ShouldFailIfTaskNotFinished() throws Exception {
        try (TaskClient client = new TaskClient("localhost", port)) {
            client.connect();

            // Soumettre une tâche très longue
            String submitPayload = codec.encode(Map.of(
                    "type", "SLEEP",
                    "data", "10000", // 10 secondes
                    "priority", "NORMAL",
                    "maxRetries", 0,
                    "timeoutMs", 30000
            ));

            Request submitReq = client.createRequest(Action.SUBMIT_TASK, submitPayload);
            Response submitResp = client.send(submitReq);
            String taskId = submitResp.payload();

            // Demander le résultat immédiatement
            Request resultReq = client.createRequest(Action.GET_TASK_RESULT, taskId);
            Response resultResp = client.send(resultReq);

            assertFalse(resultResp.success(), "Result should not be available yet");
            assertTrue(resultResp.message().contains("not finished") || resultResp.message().contains("not found"));
        }
    }

    @Test
    void submitTask_ShouldFailWithInvalidPayload() throws Exception {
        try (TaskClient client = new TaskClient("localhost", port)) {
            client.connect();

            // JSON invalide ou manquant des champs obligatoires
            String badPayload = "{\"type\": \"UNKNOWN_TYPE\", \"priority\": \"NORMAL\"}";
            Request req = client.createRequest(Action.SUBMIT_TASK, badPayload);
            Response resp = client.send(req);

            assertFalse(resp.success(), "Should fail with invalid task type");
            assertTrue(resp.message().contains("Invalid task type"), "Error message should be explicit. Got: " + resp.message());
        }
    }
}