package org.backblue.utilities;

import org.backblue.Bot;

import java.util.concurrent.ScheduledExecutorService;

public interface NdemicModule {

    String name();
    default boolean isEnabled() {
        return Bot.getBot().getModuleValue(name());
    }
    default ScheduledExecutorService scheduler() {
        return Bot.getBot().getScheduler();
    }
    default boolean equals(NdemicModule other) {
        return other.getClass() == this.getClass() && this.name().equals(other.name());
    }
    String toString();

    interface ToggleActions extends NdemicModule {
        void disable();
        void enable();
    }

}
