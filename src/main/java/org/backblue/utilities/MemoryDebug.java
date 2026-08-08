package org.backblue.utilities;

import org.backblue.core.Bot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class MemoryDebug {

    private static final Logger Log = LoggerFactory.getLogger(MemoryDebug.class);
    Bot bot;
    Runtime runtime = null;
    private long lastUsedMemoryMB = Integer.MAX_VALUE;
    private long highestMemoryMB = 0;

    public MemoryDebug(Bot bot, Set<String> args) {
        this.bot = bot;
        if (args.contains("--heap")) {
            this.runtime = Runtime.getRuntime();
            bot.getScheduler().scheduleAtFixedRate(this::sendHeap, 1, 1, TimeUnit.MINUTES);
        }
    }

    private void sendHeap() {

        long usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long allocatedMemoryMB = runtime.totalMemory() / (1024 * 1024);
        long maxMemoryMB = runtime.maxMemory() / (1024 * 1024);
        double usedPercent = (double) usedMemoryMB / maxMemoryMB;
        if (usedMemoryMB > highestMemoryMB) highestMemoryMB = usedMemoryMB;

        Log.info("Heap ({}% used, ↑{}MB): {}MB used, {}MB allocated, {}MB total", String.format("%.2f", usedPercent*100), highestMemoryMB, usedMemoryMB, allocatedMemoryMB, maxMemoryMB);
        if (usedPercent >= 0.91 && lastUsedMemoryMB != usedMemoryMB && lastUsedMemoryMB != Integer.MAX_VALUE) {
            System.gc();
            bot.getIO().send(DefinedChannel.DebugEnforcement, String.format(bot.getDebugPing().getAsMention() + " Potential excessive memory usage detected: %dMB used, up from %dMB", usedMemoryMB, lastUsedMemoryMB));
            Log.warn("Excessive memory usage: {}MB used, up from {}MB", usedMemoryMB, lastUsedMemoryMB);
        }
        if (usedPercent >= 0.96 && lastUsedMemoryMB != usedMemoryMB && lastUsedMemoryMB != Integer.MAX_VALUE) {
            bot.getIO().send(DefinedChannel.DebugEnforcement, String.format(bot.getDebugPing().getAsMention() + " Memory usage critical: %.2f%% used (%dMB), consider investigating or increasing heap size", usedPercent*100, usedMemoryMB));
            Log.error("Memory usage critical: {}% used ({}MB)!", String.format("%.2f", usedPercent*100), usedMemoryMB);
        }
        lastUsedMemoryMB = usedMemoryMB;
    }
}
