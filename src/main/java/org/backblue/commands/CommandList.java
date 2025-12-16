package org.backblue.commands;

import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.backblue.Bot;
import org.backblue.wrappers.RestrictDMs;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CommandList extends ListenerAdapter {
    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {

        if (event.getGuild().getId().equals(Bot.getBot().getDeployment().get("guild"))) {
            Bot.getBot().registerNdemicModule(new RestrictDMs());
        }

        List<CommandData> commands = new ArrayList<>();

        commands.add(Commands.slash("ping", "Ping, pong!"));
        commands.add(Commands.slash("uptime", "About this bot, version, and performance"));
        commands.add(Commands.slash("badge", "Select a role icon"));

        OptionData moduleSelection = new OptionData(OptionType.STRING, "module", "The selected module", true)
                .addChoice("INFO: Displays status of each module.", "info");
        for (String module : Bot.getBot().getModules().keySet()) {
            moduleSelection.addChoice(module + ": " + Bot.getBot().getModuleDescription(module), module);
        }

        OptionData booleanSelection = new OptionData(OptionType.BOOLEAN, "enabled", "Option of enabled or disabled", true);
        commands.add(Commands.slash("module", "Administrator: Enable or disable features of this bot")
                .addOptions(moduleSelection, booleanSelection)
                .setDefaultPermissions(DefaultMemberPermissions.DISABLED));
        commands.add(Commands.slash("data", "Administrator: View data that the bot has collected")
                .addSubcommands(new SubcommandData("user", "Administrator: View data about a user in an JSON file")
                        .addOption(OptionType.STRING, "table", "Table to view data from", true)
                        .addOption(OptionType.USER, "user", "User to view data about", true))
                .addSubcommands(new SubcommandData("all", "Administrator: View all data in an JSON file")
                        .addOption(OptionType.STRING, "table", "Table to view data from", true))
                .setDefaultPermissions(DefaultMemberPermissions.DISABLED));
        commands.add(Commands.slash("ezpunish", "Administrator: Easily punish a user.")
                .setDefaultPermissions(DefaultMemberPermissions.DISABLED));
        commands.add(Commands.slash("terminate", "Shut down this bot.")
                .setDefaultPermissions(DefaultMemberPermissions.DISABLED));
        if (event.getGuild().getId().equals(Bot.getBot().getDeployment().get("guild")) || event.getGuild().getId().equals(Bot.getBot().getAnalysis().get("guild"))) {
            event.getGuild().updateCommands().addCommands(commands).queue();
        }
    }
}