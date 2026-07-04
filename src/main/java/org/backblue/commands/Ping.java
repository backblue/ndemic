package org.backblue.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class Ping extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("ping")) {
            event.deferReply().queue();
            event.getJDA().getRestPing().queue(ping -> event.getHook().editOriginal(":ping_pong: Pong! - `" + ping + " ms`").queue());
        }
    }

}
