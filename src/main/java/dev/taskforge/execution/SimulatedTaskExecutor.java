package dev.taskforge.execution;

import dev.taskforge.task.Task;

public final class SimulatedTaskExecutor implements TaskExecutor {

    @Override
    public void execute(Task task) throws Exception {
        switch (task.type()) {
            case COMPUTATION -> compute(task.payload());
            case SLEEP -> sleep(task.payload());
            case FILE_PROCESSING, DATA_TRANSFORMATION, REPORT -> simulateWork();
        }
    }

    private void compute(String payload) {
        int iterations = parseNonNegativeInt(payload, "iterations");

        long sum = 0;
        for (int i = 0; i < iterations; i++) {
            sum += i;
        }
    }

    private void sleep(String payload) throws InterruptedException {
        long milliseconds = parseNonNegativeLong(payload, "milliseconds");
        Thread.sleep(milliseconds);
    }

    private void simulateWork() {
        long sum = 0;
        for (int i = 0; i < 10_000; i++) {
            sum += i;
        }
    }

    private static int parseNonNegativeInt(String value, String fieldName) {
        try {
            int parsed = Integer.parseInt(value);

            if (parsed < 0) {
                throw new IllegalArgumentException(fieldName + " must be >= 0");
            }

            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": " + value, exception);
        }
    }

    private static long parseNonNegativeLong(String value, String fieldName) {
        try {
            long parsed = Long.parseLong(value);

            if (parsed < 0) {
                throw new IllegalArgumentException(fieldName + " must be >= 0");
            }

            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": " + value, exception);
        }
    }
}