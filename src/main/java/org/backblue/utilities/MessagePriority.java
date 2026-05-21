package org.backblue.utilities;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.backblue.core.Bot;
import org.jspecify.annotations.NonNull;

public abstract class MessagePriority implements Comparable<MessagePriority> {

    private final int priority;
    protected final Bot bot;

    protected MessagePriority(int priority, Bot bot) {
        this.bot = bot;
        this.priority = priority;
    }

    @Override
    public int compareTo(@NonNull MessagePriority o) {
        return Integer.compare(this.priority, o.priority);
    }

    /**
     * In order, determined by priority, to see what events should be fired first.
     *
     * @param event {@code MessageReceivedEvent} event.
     * @return {@code true} if event is 'canceled', then no other listener that has priority above the current will receive this event.
     */
    public abstract boolean cancelled(MessageReceivedEvent event);
}
