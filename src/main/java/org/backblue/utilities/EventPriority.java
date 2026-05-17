package org.backblue.utilities;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.backblue.core.Bot;
import org.jspecify.annotations.NonNull;

public abstract class EventPriority implements Comparable<EventPriority> {

    private final int priority;
    protected final Bot bot;

    protected EventPriority(int priority, Bot bot) {
        this.bot = bot;
        this.priority = priority;
    }

    @Override
    public int compareTo(@NonNull EventPriority o) {
        return Integer.compare(this.priority, o.priority);
    }

    public abstract boolean run(MessageReceivedEvent event);
}
