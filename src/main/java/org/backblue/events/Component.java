package org.backblue.events;

import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.core.Bot;

public class Component extends ListenerAdapter {

    final Bot bot;
    
    public Component(Bot bot) {
        this.bot = bot;
    }

}
