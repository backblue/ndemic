package org.backblue.events;

import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.core.Bot;

public class Context extends ListenerAdapter {

    final Bot bot;

    public Context(Bot bot) {
        this.bot = bot;
    }
}
