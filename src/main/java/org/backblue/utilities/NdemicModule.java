package org.backblue.utilities;

import java.util.concurrent.ScheduledExecutorService;

public interface NdemicModule {
    boolean isEnabled();
    ScheduledExecutorService scheduler();
}
