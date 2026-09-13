package dev.taskforge.execution;

import dev.taskforge.task.Task;

public final class SimulatedTaskExecutor implements TaskExecutor {

    @Override
    public void execute(Task task) throws Exception {
        executeForResult(task);
    }

    @Override
    public String executeForResult(Task task) throws Exception {
        return switch (task.type()) {
            case COMPUTATION -> compute(task.payload());
            case SLEEP -> sleep(task.payload());
            case FILE_PROCESSING, DATA_TRANSFORMATION, REPORT -> simulateWork(task);
        };
    }

    private String compute(String payload) {
        int iterations = parseNonNegativeInt(payload, "iterations");

        long sum = 0;

        for (int i = 0; i < iterations; i++) {
            sum += i;
        }

        return "iterations=" + iterations + "; sum=" + sum;
    }

    private String sleep(String payload) throws InterruptedException {
        long milliseconds = parseNonNegativeLong(payload, "milliseconds");

        Thread.sleep(milliseconds);

        return "slept=" + milliseconds + "ms";
    }

    private String simulateWork(Task task) {
        long sum = 0;

        for (int i = 0; i < 10_000; i++) {
            sum += i;
        }

        return "type=" + task.type() + "; payloadLength=" + task.payload().length();
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