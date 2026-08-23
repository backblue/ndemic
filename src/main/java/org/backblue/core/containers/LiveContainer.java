package org.backblue.core.containers;

import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.core.Bot;
import org.backblue.enums.LiveFramework;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 *  Decentralized container/components systems.<br>
 *  Each interaction that wishes to use interactive containers
 *  are responsible for managing activity.
 */
public final class LiveContainer extends ListenerAdapter {

    private final ConcurrentHashMap<Long, @NotNull ContainerNode> containers = new ConcurrentHashMap<>();
    private static final long Timeout = TimeUnit.MINUTES.toMillis(15);

    public LiveContainer(Bot bot) {
        bot.getScheduler().scheduleWithFixedDelay(this::clean, 45, 45, TimeUnit.MINUTES);
    }

    public void applyContainerization(@NotNull Container container, Message message, @NotNull LiveFramework handler) {
        containers.put(message.getIdLong(), new ContainerNode(container, handler, System.currentTimeMillis()));
    }

    private void clean() {
        long currentTime = System.currentTimeMillis();
        containers.values().removeIf(
                node -> currentTime - node.lastUpdated > LiveContainer.Timeout
        );
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        long messageId = event.getMessage().getIdLong();
        ContainerNode registration = containers.get(messageId);
        if (registration == null) return;
        String customId = event.getButton().getCustomId();
        if (customId == null) return;
        String[] split = customId.split(";");
        registration.lastUpdated = System.currentTimeMillis();
        if (registration.handler instanceof LiveFramework.ButtonReturn k) {
            event.deferEdit().queue(hook ->
                    hook.editOriginalComponents(k.onButton(event, split)).useComponentsV2().queue()
            );
        } else if (registration.handler instanceof LiveFramework.ButtonVoid m) {
            event.deferEdit().queue();
            m.onButton(event, split);
        } else {
            throw new IllegalStateException("Unexpected button handler: " + registration.handler.getClass().getName());
        }
    }

    static class ContainerNode {

        final Container container;
        final LiveFramework handler;
        volatile long lastUpdated;

        public ContainerNode(Container container, LiveFramework handler, long lastUpdated) {
            this.container = container;
            this.handler = handler;
            this.lastUpdated = lastUpdated;
        }
    }
}
