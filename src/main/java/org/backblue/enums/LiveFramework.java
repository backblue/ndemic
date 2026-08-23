package org.backblue.enums;

import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.jspecify.annotations.NonNull;

public interface LiveFramework {

    default String identifier() {
        return this.getClass().getSimpleName();
    }

    interface ButtonReturn extends LiveFramework {
        Container onButton(@NonNull ButtonInteractionEvent event, String... actions);
    }

    interface ButtonVoid extends LiveFramework {
        void onButton(@NonNull ButtonInteractionEvent event, String... actions);
    }
}
