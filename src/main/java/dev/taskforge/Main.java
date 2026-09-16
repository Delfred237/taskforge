package dev.taskforge;

import java.time.Duration;

public final class Main {

    private Main() {
    }

    public static String applicationName() {
        return "TaskForge";
    }

    public static void main(String[] args) throws Exception {
        System.out.println(applicationName() + " bootstrap OK");
        System.out.println("Java runtime: " + Runtime.version());

        int queueCapacity = 100;
        int workerCount = 4;
        int serverPort = 8080;

        TaskForgeSystem system = new TaskForgeSystem(serverPort, workerCount, queueCapacity);

        // Hook d'arrêt propre (Ctrl+C)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Main] Shutdown signal received.");
            system.shutdown(Duration.ofSeconds(10));
            System.out.println("[Main] Goodbye.");
        }));

        system.start();

        System.out.println("[Main] TaskForge system is running. Press Ctrl+C to stop.");

        // Le thread principal peut se terminer.
        // Les threads du serveur et du pool de workers ne sont pas 'daemon',
        // donc la JVM restera en vie jusqu'à ce qu'on fasse Ctrl+C.
    }
}