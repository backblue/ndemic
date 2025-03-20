package org.backblue.commands;

import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.backblue.Core;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CommandManager extends ListenerAdapter {

    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {
        List<CommandData> commands = new ArrayList<>();

        commands.add(Commands.slash("ping", "Ping, pong!"));

        if (event.getGuild().getId().equals(Core.SETTINGS.getString("ndemicGuild")) || event.getGuild().getId().equals(Core.SETTINGS.getString("testingGuild"))) {
            event.getGuild().updateCommands().addCommands(commands).queue();
        }
    }
}
