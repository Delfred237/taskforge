package dev.taskforge.queue;

import dev.taskforge.task.Task;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class BoundedPriorityTaskQueue implements TaskQueue {

    private final int capacity;
    private final PriorityQueue<QueueEntry> queue;
    private final AtomicLong sequenceGenerator;
    private final ReentrantLock lock;
    private final Condition notEmpty;
    private final Condition notFull;

    public BoundedPriorityTaskQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Queue capacity must be greater than 0");
        }

        this.capacity = capacity;
        this.queue = new PriorityQueue<>(queueEntryComparator());
        this.sequenceGenerator = new AtomicLong(0);
        this.lock = new ReentrantLock();
        this.notEmpty = lock.newCondition();
        this.notFull = lock.newCondition();
    }

    @Override
    public boolean offer(Task task) {
        Objects.requireNonNull(task, "task must not be null");

        lock.lock();
        try {
            if (queue.size() >= capacity) {
                return false;
            }

            queue.offer(new QueueEntry(task, sequenceGenerator.getAndIncrement()));
            notEmpty.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void put(Task task) throws InterruptedException {
        Objects.requireNonNull(task, "task must not be null");

        lock.lockInterruptibly();
        try {
            while (queue.size() >= capacity) {
                notFull.await();
            }

            queue.offer(new QueueEntry(task, sequenceGenerator.getAndIncrement()));
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Task take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (queue.isEmpty()) {
                notEmpty.await();
            }

            Task task = queue.poll().task();
            notFull.signal();
            return task;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<Task> poll(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout must not be null");

        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }

        long remainingNanos = timeout.toNanos();

        lock.lockInterruptibly();
        try {
            while (queue.isEmpty()) {
                if (remainingNanos <= 0) {
                    return Optional.empty();
                }

                remainingNanos = notEmpty.awaitNanos(remainingNanos);
            }

            Task task = queue.poll().task();
            notFull.signal();
            return Optional.of(task);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public int remainingCapacity() {
        lock.lock();
        try {
            return capacity - queue.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        lock.lock();
        try {
            return queue.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Task> drain() {
        lock.lock();
        try {
            List<Task> remaining = new ArrayList<>();
            while (!queue.isEmpty()) {
                remaining.add(queue.poll().task());
            }
            // Réveiller les producteurs bloqués sur put() pour qu'ils voient que la queue est vide
            notFull.signalAll();
            return remaining;
        } finally {
            lock.unlock();
        }
    }

    private static Comparator<QueueEntry> queueEntryComparator() {
        return Comparator
                .<QueueEntry>comparingInt(entry -> entry.task().priority().weight())
                .reversed()
                .thenComparingLong(QueueEntry::sequence);
    }

    private record QueueEntry(Task task, long sequence) {

        private QueueEntry {
            Objects.requireNonNull(task, "task must not be null");
        }
    }
}