package dev.taskforge.config;

import java.util.Optional;

public final class Config {

    private Config() {
    }

    public static int serverPort() {
        return getEnvInt("SERVER_PORT", 8080);
    }

    public static int workerCount() {
        return getEnvInt("WORKER_COUNT", 4);
    }

    public static int queueCapacity() {
        return getEnvInt("QUEUE_CAPACITY", 100);
    }

    public static long taskTimeoutMs() {
        return getEnvLong("TASK_TIMEOUT_MS", 5000);
    }

    public static int maxRetries() {
        return getEnvInt("MAX_RETRIES", 2);
    }

    private static String getEnv(String key, String defaultValue) {
        return Optional.ofNullable(System.getenv(key))
                .filter(v -> !v.isBlank())
                .orElse(defaultValue);
    }

    private static int getEnvInt(String key, int defaultValue) {
        String value = getEnv(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.println("[Config] Invalid value for " + key + ": " + value + ". Using default: " + defaultValue);
            return defaultValue;
        }
    }

    private static long getEnvLong(String key, long defaultValue) {
        String value = getEnv(key, String.valueOf(defaultValue));
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            System.err.println("[Config] Invalid value for " + key + ": " + value + ". Using default: " + defaultValue);
            return defaultValue;
        }
    }
}