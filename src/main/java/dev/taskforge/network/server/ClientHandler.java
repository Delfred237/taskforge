package dev.taskforge.network.server;

import com.google.gson.Gson;
import dev.taskforge.metrics.MetricsRegistry;
import dev.taskforge.network.protocol.MessageCodec;
import dev.taskforge.network.protocol.Request;
import dev.taskforge.network.protocol.Response;
import dev.taskforge.queue.TaskSubmitter;
import dev.taskforge.result.TaskResult;
import dev.taskforge.result.TaskResultRepository;
import dev.taskforge.task.Task;
import dev.taskforge.task.TaskId;
import dev.taskforge.task.TaskPriority;
import dev.taskforge.task.TaskRepository;
import dev.taskforge.task.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

final class ClientHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);
    private static final int CLIENT_READ_TIMEOUT_MS = 5000;

    private final Socket socket;
    private final TaskSubmitter submitter;
    private final TaskRepository taskRepository;
    private final TaskResultRepository resultRepository;
    private final MetricsRegistry metrics;
    private final MessageCodec codec;
    private final Gson gson;

    ClientHandler(Socket socket,
                  TaskSubmitter submitter,
                  TaskRepository taskRepository,
                  TaskResultRepository resultRepository,
                  MetricsRegistry metrics) {
        this.socket = Objects.requireNonNull(socket);
        this.submitter = Objects.requireNonNull(submitter);
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.resultRepository = Objects.requireNonNull(resultRepository);
        this.metrics = Objects.requireNonNull(metrics);
        this.codec = new MessageCodec();
        this.gson = new Gson();
    }

    @Override
    public void run() {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            // Timeout d'inactivité : si le client ne parle pas pendant 5s, on ferme la connexion
            socket.setSoTimeout(CLIENT_READ_TIMEOUT_MS);

            String line;
            // BOUCLE KEEP-ALIVE : On traite plusieurs requêtes sur la même connexion TCP
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                metrics.increment("network.requests");
                Request request = codec.decode(line, Request.class);
                Response response = processRequest(request);

                out.println(codec.encode(response));
            }

        } catch (SocketTimeoutException e) {
            metrics.increment("network.timeouts");
            log.debug("Client connection timed out (idle): {}", socket.getRemoteSocketAddress());
        } catch (IOException e) {
            metrics.increment("network.errors");
            log.debug("IO Error with client {} (likely disconnected): {}", socket.getRemoteSocketAddress(), e.getMessage());
        } catch (Exception e) {
            metrics.increment("network.errors");
            log.error("Unexpected error handling client", e);
        }
    }

    private Response processRequest(Request request) {
        try {
            return switch (request.action()) {
                case SUBMIT_TASK -> handleSubmit(request);
                case GET_TASK_STATUS -> handleGetStatus(request);
                case GET_TASK_RESULT -> handleGetResult(request);
                case SERVER_STATUS -> handleServerStatus(request);
                case UNKNOWN -> Response.error(request.requestId(), "Unknown action");
            };
        } catch (Exception e) {
            log.error("Error processing request {}", request.requestId(), e);
            return Response.error(request.requestId(), "Internal error: " + e.getMessage());
        }
    }

    private Response handleSubmit(Request request) {
        try {
            SubmitPayload payload = gson.fromJson(request.payload(), SubmitPayload.class);

            if (payload == null || payload.type == null || payload.priority == null) {
                return Response.error(request.requestId(), "Missing required fields: type, priority");
            }

            TaskType type;
            TaskPriority priority;
            try {
                type = TaskType.valueOf(payload.type.toUpperCase());
                priority = TaskPriority.valueOf(payload.priority.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.error(request.requestId(), "Invalid task type or priority: " + e.getMessage());
            }

            Task task = Task.create(
                    type,
                    payload.data != null ? payload.data : "",
                    priority,
                    payload.maxRetries != null ? payload.maxRetries : 0,
                    Duration.ofMillis(payload.timeoutMs != null ? payload.timeoutMs : 5000)
            );

            taskRepository.save(task);
            submitter.submitBlocking(task);

            return Response.ok(request.requestId(), "Task submitted", task.id().toString());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Response.error(request.requestId(), "Server is shutting down");
        } catch (Exception e) {
            return Response.error(request.requestId(), "Invalid payload format: " + e.getMessage());
        }
    }

    private Response handleGetStatus(Request request) {
        try {
            TaskId taskId = TaskId.fromString(request.payload());
            Optional<Task> task = taskRepository.findById(taskId);

            if (task.isEmpty()) {
                return Response.error(request.requestId(), "Task not found");
            }

            return Response.ok(request.requestId(), "Status retrieved", task.get().status().name());
        } catch (Exception e) {
            return Response.error(request.requestId(), "Invalid Task ID format");
        }
    }

    private Response handleGetResult(Request request) {
        try {
            TaskId taskId = TaskId.fromString(request.payload());
            Optional<TaskResult> result = resultRepository.findByTaskId(taskId);

            if (result.isEmpty()) {
                return Response.error(request.requestId(), "Result not found or task not finished");
            }

            return Response.ok(request.requestId(), "Result retrieved", codec.encode(result.get()));
        } catch (Exception e) {
            return Response.error(request.requestId(), "Invalid Task ID format");
        }
    }

    private Response handleServerStatus(Request request) {
        String status = String.format(
                "{\"submitted\": %d, \"rejected\": %d, \"tasksInRegistry\": %d, \"resultsStored\": %d, \"metrics\": %s}",
                submitter.submittedCount(),
                submitter.rejectedCount(),
                taskRepository.count(),
                resultRepository.count(),
                new Gson().toJson(metrics.snapshot())
        );
        return Response.ok(request.requestId(), "Server status", status);
    }

    private static class SubmitPayload {
        String type;
        String data;
        String priority;
        Integer maxRetries;
        Long timeoutMs;
    }
}