package org.backblue.tasks;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;

public abstract class Task {

    public static int IDS = 0;
    public static final HashMap<Integer, Task> IDS_TO_TASK = new HashMap<>();
    public static final ArrayList<Task> TASKS_LAST_INTERVAL = new ArrayList<>();
    private final int id;
    private final long createdTimestamp;
    private long startedTimestamp;
    private long finishedTimestamp;
    protected String output = "\n";

    Task() {
        id = IDS++;
        IDS_TO_TASK.put(id, this);
        createdTimestamp = System.currentTimeMillis();
    }

    public static @NotNull HashMap<String, String> getProgress() {
        HashMap<String, String> info = new HashMap<>();
        info.put("total", String.valueOf(TASKS_LAST_INTERVAL.size()));
        int msgScanTasks = 0;
        int profileScanTasks = 0;
        int scannedWithWarning = 0;
        for (Task task : TASKS_LAST_INTERVAL) {
            if (task instanceof MessageScanTask) {
                msgScanTasks++;
            } else if (task instanceof ProfileScanTask) {
                profileScanTasks++;
            }
            if (task.output.contains(":warning:")) {
                scannedWithWarning++;
            }
        }
        info.put("msgScanTasks", String.valueOf(msgScanTasks));
        info.put("profileScanTasks", String.valueOf(profileScanTasks));
        info.put("scannedWithWarning", String.valueOf(scannedWithWarning));
        TASKS_LAST_INTERVAL.clear();
        return info;
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

    public abstract void process();
    public abstract HashMap<String, String> lookup();
}
