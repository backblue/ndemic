package org.backblue.events;

import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.core.Bot;
import org.json.JSONObject;

public class Gatekeeper extends ListenerAdapter {

    final Bot bot;

    public Gatekeeper(Bot bot, JSONObject json) {
        this.bot = bot;
    }
}
