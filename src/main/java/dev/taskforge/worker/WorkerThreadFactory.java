package dev.taskforge.worker;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

final class WorkerThreadFactory implements ThreadFactory {

    private final AtomicInteger counter = new AtomicInteger(1);

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "Worker-" + counter.getAndIncrement());
        thread.setDaemon(false);
        return thread;
    }
}