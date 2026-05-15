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

    public abstract boolean run(MessageReceivedEvent event);
}
