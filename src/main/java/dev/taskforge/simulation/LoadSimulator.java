package dev.taskforge.simulation;

import dev.taskforge.TaskForgeSystem;
import dev.taskforge.task.Task;
import dev.taskforge.task.TaskPriority;
import dev.taskforge.task.TaskStatus;
import dev.taskforge.task.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class LoadSimulator {

    private static final Logger log = LoggerFactory.getLogger(LoadSimulator.class);

    private final TaskForgeSystem system;
    private final SimulationConfig config;

    public LoadSimulator(TaskForgeSystem system, SimulationConfig config) {
        this.system = system;
        this.config = config;
    }

    public SimulationReport run() throws Exception {
        log.info("Starting load simulation: {} tasks, {} producers, {} workers, queue capacity {}",
                config.totalTasks(), config.producerCount(), config.workerCount(), config.queueCapacity());

        // CORRECTION 1 : Liste thread-safe pour éviter la corruption par les producteurs concurrents
        List<Task> allTasks = Collections.synchronizedList(new ArrayList<>());

        CountDownLatch submissionLatch = new CountDownLatch(config.totalTasks());

        ExecutorService producers = Executors.newFixedThreadPool(config.producerCount());

        Instant startTime = Instant.now();

        int tasksPerProducer = config.totalTasks() / config.producerCount();
        int remainingTasks = config.totalTasks() % config.producerCount();

        // Lancer les producteurs
        for (int p = 0; p < config.producerCount(); p++) {
            final int producerId = p;
            final int tasksForThisProducer = tasksPerProducer + (p < remainingTasks ? 1 : 0);

            producers.submit(() -> {
                try {
                    for (int t = 0; t < tasksForThisProducer; t++) {
                        Task task = Task.create(
                                TaskType.COMPUTATION,
                                String.valueOf(1000 + (producerId * 1000) + t),
                                TaskPriority.NORMAL,
                                config.maxRetries(),
                                Duration.ofMillis(config.taskTimeoutMs())
                        );

                        system.taskRepository().save(task);
                        system.submitter().submitBlocking(task);
                        allTasks.add(task);
                        submissionLatch.countDown();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error("Producer {} failed", producerId, e);
                }
            });
        }

        // Attendre que toutes les tâches soient soumises
        boolean allSubmitted = submissionLatch.await(30, TimeUnit.SECONDS);
        producers.shutdown();
        producers.awaitTermination(5, TimeUnit.SECONDS);

        if (!allSubmitted) {
            log.warn("Submission timed out. {} tasks submitted out of {}", allTasks.size(), config.totalTasks());
        } else {
            log.info("All {} tasks submitted. Waiting for completion...", allTasks.size());
        }

        // Variables pour les statistiques finales
        int currentCompleted = 0;
        int currentFailed = 0;
        int currentTimedOut = 0;
        int currentCancelled = 0;
        int currentRetried = 0;
        boolean allTerminal = false;

        // Poller jusqu'à ce que toutes les tâches soient terminales
        long deadline = System.currentTimeMillis() + 60_000; // Timeout global de 60s

        while (System.currentTimeMillis() < deadline) {
            currentCompleted = 0;
            currentFailed = 0;
            currentTimedOut = 0;
            currentCancelled = 0;
            currentRetried = 0;

            // CORRECTION 2 : Synchroniser l'itération sur une liste synchronisée
            synchronized (allTasks) {
                for (Task task : allTasks) {
                    if (task == null) continue;
                    TaskStatus status = task.status();
                    if (status == TaskStatus.COMPLETED) currentCompleted++;
                    else if (status == TaskStatus.FAILED) currentFailed++;
                    else if (status == TaskStatus.TIMED_OUT) currentTimedOut++;
                    else if (status == TaskStatus.CANCELLED) currentCancelled++;

                    currentRetried += task.retryCount();
                }
            }

            int terminalCount = currentCompleted + currentFailed + currentTimedOut + currentCancelled;
            // CORRECTION 3 : Basé sur le nombre de tâches réellement soumises et présentes dans la liste
            int totalSubmitted = allTasks.size();
            int remaining = totalSubmitted - terminalCount;

            if (remaining <= 0 && totalSubmitted > 0) {
                allTerminal = true;
                break;
            }

            Thread.sleep(100);
        }

        if (!allTerminal) {
            int terminalCount = currentCompleted + currentFailed + currentTimedOut + currentCancelled;
            log.warn("Simulation timed out. {} tasks remaining in non-terminal state.",
                    allTasks.size() - terminalCount);
        }

        Instant endTime = Instant.now();
        Duration totalTime = Duration.between(startTime, endTime);

        int finalTotal = allTasks.size();
        double throughput = totalTime.toMillis() > 0 ? (finalTotal * 1000.0) / totalTime.toMillis() : 0;

        return new SimulationReport(
                finalTotal,
                currentCompleted,
                currentFailed,
                currentTimedOut,
                currentCancelled,
                currentRetried,
                totalTime,
                throughput
        );
    }
}