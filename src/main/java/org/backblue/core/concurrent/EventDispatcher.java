package org.backblue.core.concurrent;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.message.GenericMessageEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.hooks.InterfacedEventManager;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.jetbrains.annotations.NotNull;

/** Per-shard dispatch lanes; listeners for each event still execute in registration order. */
public final class EventDispatcher extends InterfacedEventManager implements AutoCloseable {
    private final EventHenchman interactions;
    private final EventHenchman messages;
    private final EventHenchman events;

    public EventDispatcher(int shardId) {
        interactions = new EventHenchman("Bot-Interactions-" + shardId, 256);
        messages = new EventHenchman("Bot-Messages-" + shardId, 1024);
        events = new EventHenchman("Bot-Events-" + shardId, 512);
    }

    @Override
    public void handle(@NotNull GenericEvent event) {
        EventHenchman lane = event instanceof GenericInteractionCreateEvent ? interactions
                : event instanceof GenericMessageEvent ? messages : events;
        if (!lane.submit(() -> super.handle(event)) && event instanceof IReplyCallback reply
                && !reply.isAcknowledged()) {
            reply.reply("The bot is busy. Please try again shortly.").setEphemeral(true).queue();
        }
        if (event instanceof ShutdownEvent) close();
    }

    @Override
    public void close() {
        interactions.close();
        messages.close();
        events.close();
    }
}
