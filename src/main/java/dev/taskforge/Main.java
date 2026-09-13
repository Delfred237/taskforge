package dev.taskforge;

import dev.taskforge.execution.SimulatedTaskExecutor;
import dev.taskforge.execution.TaskExecutor;
import dev.taskforge.network.server.TaskServer;
import dev.taskforge.queue.BoundedPriorityTaskQueue;
import dev.taskforge.queue.TaskQueue;
import dev.taskforge.queue.TaskSubmitter;
import dev.taskforge.result.InMemoryTaskResultRepository;
import dev.taskforge.result.TaskResultRepository;
import dev.taskforge.task.InMemoryTaskRepository;
import dev.taskforge.task.TaskRepository;
import dev.taskforge.worker.WorkerPool;

public final class Main {

    private Main() {}

    public static String applicationName() {
        return "TaskForge";
    }

    public static void main(String[] args) throws Exception {
        System.out.println(applicationName() + " bootstrap OK");
        System.out.println("Java runtime: " + Runtime.version());

        int queueCapacity = 100;
        int workerCount = 4;
        int serverPort = 8080;

        TaskQueue queue = new BoundedPriorityTaskQueue(queueCapacity);
        TaskSubmitter submitter = new TaskSubmitter(queue);
        TaskRepository taskRepository = new InMemoryTaskRepository();
        TaskResultRepository resultRepository = new InMemoryTaskResultRepository();

        TaskExecutor executor = new SimulatedTaskExecutor();
        WorkerPool pool = new WorkerPool(workerCount, queue, executor);
        pool.start();
        System.out.println("[Main] WorkerPool started with " + workerCount + " workers.");

        TaskServer server = new TaskServer(serverPort, submitter, taskRepository, resultRepository);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Main] Shutting down...");
            server.stop();
            try {
                pool.shutdown(java.time.Duration.ofSeconds(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("[Main] Goodbye.");
        }));

        System.out.println("[Main] Starting TCP Server on port " + serverPort + "...");
        server.startAsync(); // Démarre le serveur dans un thread séparé

        // Le thread principal doit rester en vie pour que la JVM ne s'arrête pas
        // On peut bloquer sur un CountDownLatch ou simplement faire un Thread.sleep(Long.MAX_VALUE)
        // Ici, on laisse le thread principal terminer, mais les threads du serveur et du pool
        // ne sont pas 'daemon', donc la JVM restera en vie jusqu'à ce qu'on fasse Ctrl+C.
    }
}