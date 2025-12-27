package org.backblue.utilities;

import org.backblue.Bot;

import java.util.concurrent.ScheduledExecutorService;

public interface NdemicModule {

    String toString();
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

    interface ToggleActions extends NdemicModule {
        void disable();
        void enable();
    }

    interface Azure extends NdemicModule {
        enum AzureProperty {
            Hate,
            Sexual,
            SelfHarm,
            Violence
        }
    }

}
