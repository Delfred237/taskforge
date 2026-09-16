package dev.taskforge;

import dev.taskforge.config.Config;

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

        int queueCapacity = Config.queueCapacity();
        int workerCount = Config.workerCount();
        int serverPort = Config.serverPort();

        TaskForgeSystem system = new TaskForgeSystem(serverPort, workerCount, queueCapacity);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Main] Shutdown signal received.");
            system.shutdown(Duration.ofSeconds(10));
            System.out.println("[Main] Goodbye.");
        }));

        system.start();

        System.out.println("[Main] TaskForge system is running. Press Ctrl+C to stop.");
    }
}