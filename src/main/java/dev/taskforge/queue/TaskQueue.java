package dev.taskforge.queue;

import dev.taskforge.task.Task;

import java.time.Duration;
import java.util.Optional;

public interface TaskQueue {

    boolean offer(Task task);

    void put(Task task) throws InterruptedException;

    Task take() throws InterruptedException;

    Optional<Task> poll(Duration timeout) throws InterruptedException;

    int size();

    int capacity();

    int remainingCapacity();

    boolean isEmpty();
}