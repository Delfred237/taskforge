package dev.taskforge.task;

public enum TaskPriority {
    LOW(0),
    NORMAL(1),
    HIGH(2),
    CRITICAL(3);

    private final int weight;

    TaskPriority(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }

    public boolean isHigherThan(TaskPriority other) {
        return this.weight > other.weight;
    }
}