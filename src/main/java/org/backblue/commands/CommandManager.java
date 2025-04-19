package org.backblue.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.backblue.Core;
import org.backblue.libraries.ProfileScanJob;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CommandManager extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            if (event.getUser().getAvatarUrl() == null) {
                return;
            }
            if (Core.SAFETY.getJSONObject("scanProfile").getBoolean("onSlash")) {
                new ProfileScanJob(event.getUser().getId(), "slash");
            }
        }
    }
    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {
        List<CommandData> commands = new ArrayList<>();

        commands.add(Commands.slash("ping", "Ping, pong!"));
        commands.add(Commands.slash("uptime", "About this bot & view bot performance"));

        OptionData moduleSelection = new OptionData(OptionType.STRING, "module", "The selected module", true)
                .addChoice("INFO: Displays status of each module. More details with 'true'", "info");

        for (String module : Core.MODULES_DESC.keySet()) {
            moduleSelection.addChoice(module + ": " + Core.MODULES_DESC.get(module), module);
        }
        OptionData booleanSelection = new OptionData(OptionType.BOOLEAN, "enabled", "Option of enabled or disabled", true);

        commands.add(Commands.slash("module", "Administrator: Enable or disable features of this bot")
                .addOptions(moduleSelection, booleanSelection)
                .setDefaultPermissions(DefaultMemberPermissions.DISABLED));
        commands.add(Commands.slash("safety", "Administrator: View current, upcoming queue for safety scanning tasks")
                .setDefaultPermissions(DefaultMemberPermissions.DISABLED)
                .addSubcommands(new SubcommandData("queue", "Administrator: View current queue for safety scanning tasks"))
                .addSubcommands(new SubcommandData("dequeue", "Administrator: Force-run the next job in queue. Dequeue is performed automatically"))
                .addSubcommands(new SubcommandData("skip", "Administrator: Skips the next job")));
        commands.add(Commands.slash("safetylookup", "Administrator: Lookup a job by its ID")
                .addOption(OptionType.INTEGER, "identifier", "Enter Job ID.", true)
                .setDefaultPermissions(DefaultMemberPermissions.DISABLED));
        if (event.getGuild().getId().equals(Core.DEPLOYMENT.get("guild"))) {
            event.getGuild().updateCommands().addCommands(commands).queue();
        }
    }
}
