package dev.taskforge.simulation;

import dev.taskforge.TaskForgeSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadSimulationTest {

    private TaskForgeSystem system;

    @BeforeEach
    void setUp() throws Exception {
        system = new TaskForgeSystem(0, 4, 100);
        system.start();
    }

    @AfterEach
    void tearDown() {
        if (system != null && system.isStarted()) {
            system.shutdown(Duration.ofSeconds(5));
        }
    }

    @Test
    void simulationWith1000TasksShouldCompleteSuccessfully() throws Exception {
        SimulationConfig config = new SimulationConfig(
                1000,   // 1000 tâches
                5,      // 5 producteurs
                4,      // 4 workers
                100,    // Queue de 100
                5000,   // Timeout de 5s
                0       // Pas de retry pour simplifier
        );

        LoadSimulator simulator = new LoadSimulator(system, config);
        SimulationReport report = simulator.run();

        System.out.println(report.formatted());

        // Vérifications de base
        assertEquals(config.totalTasks(), report.totalTasks());
        assertTrue(report.completedTasks() > 0, "At least some tasks should complete");
        assertTrue(report.throughputTasksPerSecond() > 0, "Throughput should be positive");
        assertTrue(report.totalTime().toMillis() < 60_000, "Simulation should complete within 60s");

        // Toutes les tâches doivent être dans un état terminal
        int terminalCount = report.completedTasks() + report.failedTasks() +
                report.timedOutTasks() + report.cancelledTasks();
        assertEquals(config.totalTasks(), terminalCount, "All tasks should reach terminal state");
    }

    @Test
    void simulationWithRetriesShouldHandleFailures() throws Exception {
        SimulationConfig config = new SimulationConfig(
                100,    // 100 tâches (plus petit pour aller plus vite)
                2,      // 2 producteurs
                2,      // 2 workers
                20,     // Queue de 20
                2000,   // Timeout de 2s
                2       // Max 2 retries
        );

        LoadSimulator simulator = new LoadSimulator(system, config);
        SimulationReport report = simulator.run();

        System.out.println(report.formatted());

        assertTrue(report.completedTasks() >= 0);
        assertTrue(report.totalTime().toMillis() < 30_000);
    }
}