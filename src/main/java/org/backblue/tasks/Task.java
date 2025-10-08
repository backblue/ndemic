package org.backblue.tasks;
import org.backblue.Bot;

import java.util.HashMap;

public abstract class Task {

    public static int IDS = 0;
    public static HashMap<Integer, Task> IDS_TO_TASK;
    private final int id;
    private final long createdTimestamp;
    private long startedTimestamp;
    private long finishedTimestamp;
    protected String output = "\n";
    protected boolean silenced = false;

    public static class Stats {
        public static int bsky = 0;
        public static int message = 0;
        public static int profile = 0;
    }

    Task() {
        id = IDS++;
        if (Bot.getBot().getTasks().getBoolean("saveAfterUse")) {
            if (IDS_TO_TASK == null) {
                IDS_TO_TASK = new HashMap<>();
            }
            IDS_TO_TASK.put(id, this);
        }
        createdTimestamp = System.currentTimeMillis();
        switch (this) {
            case MessageScanTask messageScanTask -> Stats.message++;
            case ProfileScanTask profileScanTask -> Stats.profile++;
            case BlueSkyReadTask blueSkyReadTask -> Stats.bsky++;
            default -> {}
        }
    }

    @Override
    public final String toString() {
        return id + " - " + this.getClass().getSimpleName();
    }

    @Override
    protected final Task clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException("Unsupported, create a new instance instead.");
    }

    public int getId() {
        return id;
    }
    public long getCreated() {
        return createdTimestamp;
    }
    public long getStarted() {
        return startedTimestamp;
    }
    public long getFinished() {
        return finishedTimestamp;
    }
    protected final void markStarted() {
        startedTimestamp = System.currentTimeMillis();
    }
    protected final void appendOutput(String text) {
        output += text + "\n";
    }
    public boolean isSilenced() {
        return silenced;
    }

    protected final void markDone() {
        finishedTimestamp = System.currentTimeMillis();
        output += " :white_check_mark: Completed";
    }

    protected final void markDoneWithWarning(String warning) {
        finishedTimestamp = System.currentTimeMillis();
        output += " :warning: " + warning + " ";
    }

    protected final HashMap<String, String> lookupBase() {
        HashMap<String, String> info = new HashMap<>();
        info.put("id", String.valueOf(id));
        info.put("createdTimestamp", String.valueOf(createdTimestamp));
        info.put("startedTimestamp", String.valueOf(startedTimestamp));
        info.put("finishedTimestamp", String.valueOf(finishedTimestamp));
        info.put("output", output);
        info.put("class", this.getClass().getSimpleName());
        return info;
    }

    protected final void completedTaskCreation() {
        if (Bot.getBot().getTasks().getBoolean("queueItems")) {
            Bot.getBot().getTaskQueue().add(this);
        } else {
            this.process();
        }
    }

    public abstract void process();
    public abstract HashMap<String, String> lookup();
}
