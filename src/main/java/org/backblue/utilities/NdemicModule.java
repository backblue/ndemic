package org.backblue.utilities;

import org.backblue.Bot;

import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

public interface NdemicModule {

    String name();
    default boolean isEnabled() {
        return Bot.getBot().getModuleValue(name());
    }
    default ScheduledExecutorService scheduler() {
        return Bot.getBot().getScheduler();
    }
    default Set<NdemicModule> set() {
        return Bot.getBot().getNdemicModules();
    }

    interface ToggleActions {
        void disable();
        void enable();
    }

}
