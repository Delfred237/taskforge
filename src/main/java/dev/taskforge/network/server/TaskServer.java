package dev.taskforge.network.server;

import dev.taskforge.metrics.MetricsRegistry;
import dev.taskforge.queue.TaskSubmitter;
import dev.taskforge.result.TaskResultRepository;
import dev.taskforge.task.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class TaskServer {

    private static final Logger log = LoggerFactory.getLogger(TaskServer.class);
    private static final int ACCEPT_TIMEOUT_MS = 1000;
    private static final int MAX_CONNECTIONS = 50;

    private final int port;
    private final TaskSubmitter submitter;
    private final TaskRepository taskRepository;
    private final TaskResultRepository resultRepository;
    private final MetricsRegistry metrics;

    private ServerSocket serverSocket;
    private ExecutorService connectionExecutor;
    private Thread acceptorThread;
    private volatile boolean running;

    public TaskServer(int port,
                      TaskSubmitter submitter,
                      TaskRepository taskRepository,
                      TaskResultRepository resultRepository,
                      MetricsRegistry metrics) {
        this.port = port;
        this.submitter = Objects.requireNonNull(submitter);
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.resultRepository = Objects.requireNonNull(resultRepository);
        this.metrics = Objects.requireNonNull(metrics);
    }

    public synchronized void startAsync() throws IOException {
        if (running) {
            throw new IllegalStateException("Server is already running");
        }

        serverSocket = new ServerSocket(port);
        serverSocket.setSoTimeout(ACCEPT_TIMEOUT_MS);

        connectionExecutor = Executors.newFixedThreadPool(MAX_CONNECTIONS);
        running = true;

        acceptorThread = new Thread(this::acceptLoop, "TaskServer-Acceptor");
        acceptorThread.start();

        log.info("TaskServer listening on port {}", getPort());
    }

    public int getPort() {
        if (serverSocket == null) {
            return port;
        }
        return serverSocket.getLocalPort();
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }

        running = false;
        log.info("TaskServer stopping...");

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.error("Error closing server socket", e);
        }

        if (connectionExecutor != null) {
            connectionExecutor.shutdown();
            try {
                if (!connectionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    connectionExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                connectionExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (acceptorThread != null) {
            try {
                acceptorThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        log.info("TaskServer stopped");
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                // Passage correct des 5 arguments au ClientHandler
                connectionExecutor.submit(new ClientHandler(
                        clientSocket,
                        submitter,
                        taskRepository,
                        resultRepository,
                        metrics
                ));
            } catch (SocketException e) {
                if (!running) {
                    break;
                }
            } catch (IOException e) {
                if (running) {
                    log.error("Error accepting client", e);
                }
            }
        }
    }
}