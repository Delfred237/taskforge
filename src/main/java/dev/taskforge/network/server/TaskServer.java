package dev.taskforge.network.server;

import dev.taskforge.queue.TaskSubmitter;
import dev.taskforge.result.TaskResultRepository;
import dev.taskforge.task.TaskRepository;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class TaskServer {

    private static final int ACCEPT_TIMEOUT_MS = 1000;
    private static final int MAX_CONNECTIONS = 50;

    private final int port;
    private final TaskSubmitter submitter;
    private final TaskRepository taskRepository;
    private final TaskResultRepository resultRepository;

    private ServerSocket serverSocket;
    private ExecutorService connectionExecutor;
    private Thread acceptorThread;
    private volatile boolean running;

    public TaskServer(int port,
                      TaskSubmitter submitter,
                      TaskRepository taskRepository,
                      TaskResultRepository resultRepository) {
        this.port = port;
        this.submitter = Objects.requireNonNull(submitter);
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.resultRepository = Objects.requireNonNull(resultRepository);
    }

    public synchronized void startAsync() throws IOException {
        if (running) {
            throw new IllegalStateException("Server is already running");
        }

        serverSocket = new ServerSocket(port);
        // Permet à la boucle accept() de se réveiller toutes les secondes pour vérifier 'running'
        serverSocket.setSoTimeout(ACCEPT_TIMEOUT_MS);

        connectionExecutor = Executors.newFixedThreadPool(MAX_CONNECTIONS);
        running = true;

        acceptorThread = new Thread(this::acceptLoop, "TaskServer-Acceptor");
        acceptorThread.start();

        System.out.println("[TaskServer] Listening on port " + getPort());
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

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[TaskServer] Error closing server socket: " + e.getMessage());
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

        System.out.println("[TaskServer] Stopped.");
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                connectionExecutor.submit(new ClientHandler(clientSocket, submitter, taskRepository, resultRepository));
            } catch (SocketException e) {
                // Se produit quand on ferme le serverSocket ou que le timeout expire
                if (running) {
                    // Timeout normal, on continue la boucle
                } else {
                    // Arrêt demandé
                    break;
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("[TaskServer] Error accepting client: " + e.getMessage());
                }
            }
        }
    }
}