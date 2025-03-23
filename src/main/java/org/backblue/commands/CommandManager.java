package org.backblue.commands;

import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.backblue.Core;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CommandManager extends ListenerAdapter {

    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {
        List<CommandData> commands = new ArrayList<>();

        commands.add(Commands.slash("ping", "Ping, pong!"));
        commands.add(Commands.slash("uptime", "View bot process performance"));

        OptionData moduleSelection = new OptionData(OptionType.STRING, "module", "The selected module", true)
                .addChoice("INFO: Displays status of each module. More details with 'true'", "info");

        for (String module : Core.MODULES_DESC.keySet()) {
            moduleSelection.addChoice(module + ": " + Core.MODULES_DESC.get(module), module);
        }
        OptionData booleanSelection = new OptionData(OptionType.BOOLEAN, "enabled", "Option of enabled or disabled", true);
        commands.add(Commands.slash("module", "Enable or disable features of this bot")
                .addOptions(moduleSelection, booleanSelection)
                .setDefaultPermissions(DefaultMemberPermissions.DISABLED));

        if (event.getGuild().getId().equals(Core.SETTINGS.getString("ndemicGuild")) || event.getGuild().getId().equals(Core.SETTINGS.getString("testingGuild"))) {
            event.getGuild().updateCommands().addCommands(commands).queue();
        }
    }
}
