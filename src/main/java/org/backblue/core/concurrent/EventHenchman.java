package org.backblue.core.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Ordered, bounded work queue. Overload never runs work on the submitting thread. */
public final class EventHenchman implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(EventHenchman.class);
    private final ThreadPoolExecutor executor;
    private final String name;

    public EventHenchman(String name, int capacity) {
        this.name = name;
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity), task -> {
                    Thread thread = new Thread(task, name);
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public boolean submit(Runnable task) {
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    LOG.error("Task failed in {}", name, e);
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            LOG.warn("{} rejected work: queue full or lane shut down", name);
            return false;
        }
    }

    /** Stop accepting work and drain already accepted tasks. */
    @Override
    public void close() {
        executor.shutdown();
    }
}
