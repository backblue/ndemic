package org.backblue.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.backblue.extension.Deployable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class Ping extends ListenerAdapter implements Deployable {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("ping")) {
            event.deferReply().queue();
            event.getJDA().getRestPing().queue(ping -> event.getHook().editOriginal(":ping_pong: Pong! - `" + ping + " ms`").queue());
        }
    }

    @Override
    public List<CommandData> cmds() {
        return List.of(Commands.slash("ping", "Ping, pong!"));
    }
}
