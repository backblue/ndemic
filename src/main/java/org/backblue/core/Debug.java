package org.backblue.core;

import org.backblue.utilities.DefinedChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class Debug {

    private static final Logger Log = LoggerFactory.getLogger(Debug.class);
    Bot bot;
    Runtime runtime = Runtime.getRuntime();
    private long lastUsedMemoryMB = Integer.MAX_VALUE;


    public Debug(Bot bot, Set<String> args) {
        this.bot = bot;
        if (args.contains("--heap")) {
            bot.getScheduler().scheduleAtFixedRate(this::sendHeap, 1, 1, TimeUnit.MINUTES);
        }
    }

    private void sendHeap() {

        long usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long allocatedMemoryMB = runtime.totalMemory() / (1024 * 1024);
        long maxMemoryMB = runtime.maxMemory() / (1024 * 1024);
        double usedPercent = (double) usedMemoryMB / maxMemoryMB;
        Log.info("Heap ({}% used): {}MB used, {}MB allocated, {}MB total", String.format("%.2f", usedPercent*100), usedMemoryMB, allocatedMemoryMB, maxMemoryMB);
        if (usedPercent >= 0.6 && lastUsedMemoryMB != Integer.MAX_VALUE) {
            bot.getIO().send(DefinedChannel.DebugEnforcement, String.format(bot.getDebugPing().getAsMention() + " Potential excessive memory usage detected: %dMB used, up from %dMB", usedMemoryMB, lastUsedMemoryMB));
            Log.warn("Potential excessive memory usage detected: {}MB used, up from {}MB", usedMemoryMB, lastUsedMemoryMB);
        }
        if (usedPercent >= 0.85 && lastUsedMemoryMB != Integer.MAX_VALUE) {
            bot.getIO().send(DefinedChannel.DebugEnforcement, String.format(bot.getDebugPing().getAsMention() + " Memory usage critical: %.2f%% used (%dMB), consider investigating or increasing heap size", usedPercent*100, usedMemoryMB));
            Log.error("Memory usage critical: {}% used ({}MB), consider investigating or increasing heap size", String.format("%.2f", usedPercent*100), usedMemoryMB);
        }
        lastUsedMemoryMB = usedMemoryMB;
    }
}
