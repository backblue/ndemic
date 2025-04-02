package org.backblue;

import org.backblue.libraries.Job;

import java.util.concurrent.TimeUnit;

public class Processing extends Thread {

    @Override
    public void run() {
        try {
            if (Core.MODULES.get("safetyFeatures")) {
                while (true) {
                    Job.process();
                    Thread.sleep(TimeUnit.MILLISECONDS.toMillis(Core.SAFETY.getLong("milliseconds")));
                }
            }

        } catch (InterruptedException ignored) {
        }
    }
}
