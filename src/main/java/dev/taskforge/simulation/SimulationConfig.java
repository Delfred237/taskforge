package dev.taskforge.simulation;

public record SimulationConfig(
        int totalTasks,
        int producerCount,
        int workerCount,
        int queueCapacity,
        long taskTimeoutMs,
        int maxRetries
) {
    public SimulationConfig {
        if (totalTasks <= 0) throw new IllegalArgumentException("totalTasks must be > 0");
        if (producerCount <= 0) throw new IllegalArgumentException("producerCount must be > 0");
        if (workerCount <= 0) throw new IllegalArgumentException("workerCount must be > 0");
        if (queueCapacity <= 0) throw new IllegalArgumentException("queueCapacity must be > 0");
        if (taskTimeoutMs <= 0) throw new IllegalArgumentException("taskTimeoutMs must be > 0");
        if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be >= 0");
    }

    public static SimulationConfig defaultConfig() {
        return new SimulationConfig(
                1000,   // 1000 tâches
                5,      // 5 producteurs
                4,      // 4 workers
                100,    // Queue de 100
                5000,   // Timeout de 5s par tâche
                2       // Max 2 retries
        );
    }
}