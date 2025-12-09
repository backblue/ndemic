package org.backblue.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Bot;
import org.jetbrains.annotations.NotNull;

public class Terminate extends ListenerAdapter {
    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("terminate") && event.getUser().getId().equals(Bot.getBot().getSettings().getString("owner"))) {
            System.exit(96);
        }
    }

}
