package org.backblue.events.jobs;


import org.backblue.Core;

import java.util.HashMap;

public class JobRunner extends Job implements Runnable {

    @Override
    public void run() {
        while (true) {
            try {
                if (Job.QUEUE.peek() != null) {
                    Job.QUEUE.peek().process();
                }
                Thread.sleep(Core.SAFETY.getLong("interval")*1000L);
            } catch (Exception e) {
                System.out.println("Error in JobRunner: " + e.getMessage());
            }

        }
    }

    public JobRunner() {
        System.out.println("Created Instance: JobRunner");
    }

    @Override
    public void process() {

    }

    @Override
    public HashMap<String, String> lookup() {
        return null;
    }

    @Override
    public String toString() {
        return null;
    }
}
