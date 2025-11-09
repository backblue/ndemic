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

        OptionData moduleSelection = new OptionData(OptionType.STRING, "module", "The selected module", true)
                .addChoice("INFO: Displays status of each module.", "info");
        for (String module : Bot.getBot().getModules().keySet()) {
            moduleSelection.addChoice(module + ": " + Bot.getBot().getModuleDescription(module), module);
        }

        OptionData booleanSelection = new OptionData(OptionType.BOOLEAN, "enabled", "Option of enabled or disabled", true);
        commands.add(Commands.slash("module", "Administrator: Enable or disable features of this bot")
                .addOptions(moduleSelection, booleanSelection)
                .setDefaultPermissions(DefaultMemberPermissions.DISABLED));
        commands.add(Commands.slash("tasks", "Administrator: View current, upcoming tasks")
                .setDefaultPermissions(DefaultMemberPermissions.DISABLED)
                .addSubcommands(new SubcommandData("queue", "Administrator: View the current task queue"))
                .addSubcommands(new SubcommandData("stats", "Administrator: View statistics about tasks"))
                .addSubcommands(new SubcommandData("lookup", "Administrator: Lookup a task by its ID")
                        .addOption(OptionType.INTEGER, "identifier", "Enter Task ID.", true)));
        commands.add(Commands.slash("data", "Administrator: View data that the bot has collected")
                .addSubcommands(new SubcommandData("user", "Administrator: View data about a user in an JSON file")
                        .addOption(OptionType.STRING, "table", "Table to view data from", true)
                        .addOption(OptionType.USER, "user", "User to view data about", true))
                .addSubcommands(new SubcommandData("all", "Administrator: View all data in an JSON file")
                        .addOption(OptionType.STRING, "table", "Table to view data from", true))
                .setDefaultPermissions(DefaultMemberPermissions.DISABLED));
        commands.add(Commands.slash("ezpunish", "Administrator: Softban a user, then logs the action. Proof required")
                .addOption(OptionType.USER, "user", "User to kick", true)
                .addOption(OptionType.INTEGER, "ruleid", "Rule Violation", true)
                .addOption(OptionType.BOOLEAN, "ban", "Permanently removes user from guild", true)
                .addOption(OptionType.ATTACHMENT, "evidenceimage", "Evidence: as attachment (eg img)", false)
                .addOption(OptionType.STRING, "evidencetext", "Evidence: as text (eg link)", false)
                .setDefaultPermissions(DefaultMemberPermissions.DISABLED));
        if (event.getGuild().getId().equals(Bot.getBot().getDeployment().get("guild")) || event.getGuild().getId().equals(Bot.getBot().getAnalysis().get("guild"))) {
            event.getGuild().updateCommands().addCommands(commands).queue();
        }
    }
}