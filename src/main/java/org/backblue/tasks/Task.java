package org.backblue.tasks;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Stack;

public abstract class Task {

    private static int tasks = 0;
    private static final Queue<Task> TASK_QUEUE = new LinkedList<>();
    private static final Stack<Task> TASK_COMPLETED = new Stack<>();
    private static final HashMap<Integer, Task> ID_TO_TASK = new HashMap<>();

    protected int id;
    protected long createdAt;
    protected long startedAt;
    protected long doneAt;

    private String output;

    public static Queue<Task> getTasksQueue() {
        return TASK_QUEUE;
    }

    public static Stack<Task> getTasksCompleted() {
        return TASK_COMPLETED;
    }

    public static Task getTaskById(int id) {
        return ID_TO_TASK.get(id);
    }
    protected Task() {
        this.id = tasks++;
        this.createdAt = System.currentTimeMillis();
        this.startedAt = -1;
        this.doneAt = -1;
        ID_TO_TASK.put(this.id, this);
    }

    protected void appendOutput(String output) {
        this.output += output + "\n";
    }

    protected final void markInvalid(String reason) {
        this.doneAt = System.currentTimeMillis();
        appendOutput(":x: Task cannot be completed: " + reason);
    }

    protected final void markDoneWithProblem(String reason) {
        this.doneAt = System.currentTimeMillis();
        appendOutput(":warning: Task completed with a problem: " + reason);
    }

    protected final void markDone() {
        this.doneAt = System.currentTimeMillis();
        appendOutput(":white_check_mark: Task completed");
    }

    public HashMap<String, String> lookup() {
        HashMap<String, String> map = new HashMap<>();
        map.put("id", String.valueOf(this.id));
        map.put("output", this.output);
        map.put("created", String.valueOf(this.createdAt));
        map.put("started", String.valueOf(this.startedAt));
        map.put("completed", String.valueOf(this.doneAt));
        map.put("type", this.getClass().getSimpleName());
        return map;
    }

    @Override
    public abstract String toString();
    public abstract void process();


}
