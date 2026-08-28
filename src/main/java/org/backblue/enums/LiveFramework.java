package org.backblue.enums;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponent;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

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

    interface Pagination extends LiveFramework {
        int PREVIOUS_BUTTON = -1;
        int NEXT_BUTTON = -2;

        Container onButtonNext(long messageId);
        Container onButtonPrevious(long messageId);

        default ActionRow buildPagingRows(int currentPage, int elementsPerPage, int elementsMax) {
            List<ActionRowChildComponent> row = new ArrayList<>();
            int maxPages = (int) Math.ceil((double) elementsMax / elementsPerPage);
            if (currentPage <= 1) {
                row.add(Button.secondary(identifier()+";"+PREVIOUS_BUTTON, "<").asDisabled());
            } else row.add(Button.secondary(identifier()+";"+PREVIOUS_BUTTON, "<"));
            row.add(Button.secondary(identifier()+";paging", "Page " + currentPage + "/" + maxPages).asDisabled());
            if (currentPage >= maxPages) {
                row.add(Button.secondary(identifier()+";"+NEXT_BUTTON, ">").asDisabled());
            } else row.add(Button.secondary(identifier()+";"+NEXT_BUTTON, ">"));
            return ActionRow.of(row);
        }
    }
}
