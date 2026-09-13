package dev.taskforge.network.server;

import com.google.gson.Gson;
import dev.taskforge.network.protocol.MessageCodec;
import dev.taskforge.network.protocol.Request;
import dev.taskforge.network.protocol.Response;
import dev.taskforge.queue.TaskSubmitter;
import dev.taskforge.result.TaskResultRepository;
import dev.taskforge.task.Task;
import dev.taskforge.task.TaskId;
import dev.taskforge.task.TaskPriority;
import dev.taskforge.task.TaskRepository;
import dev.taskforge.task.TaskType;
import dev.taskforge.result.TaskResult;

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

    private static final int CLIENT_READ_TIMEOUT_MS = 5000; // 5 secondes pour recevoir la requête

    private final Socket socket;
    private final TaskSubmitter submitter;
    private final TaskRepository taskRepository;
    private final TaskResultRepository resultRepository;
    private final MessageCodec codec;
    private final Gson gson;

    ClientHandler(Socket socket,
                  TaskSubmitter submitter,
                  TaskRepository taskRepository,
                  TaskResultRepository resultRepository) {
        this.socket = Objects.requireNonNull(socket);
        this.submitter = Objects.requireNonNull(submitter);
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.resultRepository = Objects.requireNonNull(resultRepository);
        this.codec = new MessageCodec();
        this.gson = new Gson();
    }

    @Override
    public void run() {
        // Try-with-resources garantit la fermeture du socket et des streams
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            // Protège contre les clients qui se connectent mais n'envoient rien
            socket.setSoTimeout(CLIENT_READ_TIMEOUT_MS);

            String line = in.readLine();
            if (line == null || line.isBlank()) {
                return; // Client déconnecté proprement ou requête vide
            }

            Request request = codec.decode(line, Request.class);
            Response response = processRequest(request);

            out.println(codec.encode(response));

        } catch (SocketTimeoutException e) {
            System.err.println("[ClientHandler] Client timed out: " + socket.getRemoteSocketAddress());
        } catch (IOException e) {
            // Déconnexion brutale, réseau coupé, etc.
            System.err.println("[ClientHandler] IO Error with client " + socket.getRemoteSocketAddress() + ": " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[ClientHandler] Unexpected error: " + e.getMessage());
            e.printStackTrace();
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
            return Response.error(request.requestId(), "Internal error: " + e.getMessage());
        }
    }

    private Response handleSubmit(Request request) throws InterruptedException {
        SubmitPayload payload = gson.fromJson(request.payload(), SubmitPayload.class);

        TaskType type = TaskType.valueOf(payload.type.toUpperCase());
        TaskPriority priority = TaskPriority.valueOf(payload.priority.toUpperCase());

        Task task = Task.create(
                type,
                payload.data,
                priority,
                payload.maxRetries != null ? payload.maxRetries : 0,
                Duration.ofMillis(payload.timeoutMs != null ? payload.timeoutMs : 5000)
        );

        taskRepository.save(task);
        submitter.submitBlocking(task);

        return Response.ok(request.requestId(), "Task submitted", task.id().toString());
    }

    private Response handleGetStatus(Request request) {
        TaskId taskId = TaskId.fromString(request.payload());
        Optional<Task> task = taskRepository.findById(taskId);

        if (task.isEmpty()) {
            return Response.error(request.requestId(), "Task not found");
        }

        return Response.ok(request.requestId(), "Status retrieved", task.get().status().name());
    }

    private Response handleGetResult(Request request) {
        TaskId taskId = TaskId.fromString(request.payload());
        Optional<TaskResult> result = resultRepository.findByTaskId(taskId);

        if (result.isEmpty()) {
            return Response.error(request.requestId(), "Result not found or task not finished");
        }

        return Response.ok(request.requestId(), "Result retrieved", codec.encode(result.get()));
    }

    private Response handleServerStatus(Request request) {
        String status = String.format(
                "{\"submitted\": %d, \"rejected\": %d, \"tasksInRegistry\": %d, \"resultsStored\": %d}",
                submitter.submittedCount(),
                submitter.rejectedCount(),
                taskRepository.count(),
                resultRepository.count()
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