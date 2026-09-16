package dev.taskforge.simulation;

import java.time.Duration;

public record SimulationReport(
        int totalTasks,
        int completedTasks,
        int failedTasks,
        int timedOutTasks,
        int cancelledTasks,
        int retriedTasks,
        Duration totalTime,
        double throughputTasksPerSecond
) {
    public String formatted() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== LOAD SIMULATION REPORT ==========\n");
        sb.append(String.format("Total tasks:        %d%n", totalTasks));
        sb.append(String.format("Completed:          %d (%.1f%%)%n", completedTasks, percent(completedTasks)));
        sb.append(String.format("Failed:             %d (%.1f%%)%n", failedTasks, percent(failedTasks)));
        sb.append(String.format("Timed out:          %d (%.1f%%)%n", timedOutTasks, percent(timedOutTasks)));
        sb.append(String.format("Cancelled:          %d (%.1f%%)%n", cancelledTasks, percent(cancelledTasks)));
        sb.append(String.format("Retried:            %d%n", retriedTasks));
        sb.append(String.format("Total time:         %d ms%n", totalTime.toMillis()));
        sb.append(String.format("Throughput:         %.2f tasks/sec%n", throughputTasksPerSecond));
        sb.append("============================================\n");
        return sb.toString();
    }

    private double percent(int count) {
        return totalTasks == 0 ? 0 : (count * 100.0) / totalTasks;
    }
}