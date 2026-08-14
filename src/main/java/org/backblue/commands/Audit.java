package org.backblue.commands;

import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.core.Bot;

public class Audit extends ListenerAdapter {

    final Bot bot;

    public Audit(Bot bot) {
        this.bot = bot;
    }


}
