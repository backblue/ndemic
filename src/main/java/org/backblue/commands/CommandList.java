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
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CommandList extends ListenerAdapter {
    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {
        List<CommandData> commands = new ArrayList<>();

        commands.add(Commands.slash("ping", "Ping, pong!"));
        commands.add(Commands.slash("uptime", "About this bot, version, and performance"));

        OptionData moduleSelection = new OptionData(OptionType.STRING, "module", "The selected module", true)
                .addChoice("INFO: Displays status of each module.", "info");
        for (String module : Bot.getBot().getModules().keySet()) {
            moduleSelection.addChoice(module + ": " + Bot.getBot().getModuleDescription(module), module);
        }
        OptionData rulesSelection = new OptionData(OptionType.STRING, "rule", "The selected rule", true);
        Path path = Path.of("data/rulebook.json");
        if (Files.exists(path)) {
            try {
                JSONArray rulebook = new JSONObject(Files.readString(path)).getJSONArray("content");
                for (int i = 0; i < rulebook.length(); i++) {
                    JSONObject rule = rulebook.getJSONObject(i);
                    rulesSelection.addChoice(rule.getString("shortTitle"), String.valueOf(rule.getInt("id")));
                }
            } catch (JSONException | IOException ignored) {
            }
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
                .addSubcommands(new SubcommandData("overwrite", "Administrator: Force write update of caches"))
                .addSubcommands(new SubcommandData("user", "Administrator: View data about a user in an JSON file")
                        .addOption(OptionType.STRING, "table", "Table to view data from", true)
                        .addOption(OptionType.USER, "user", "User to view data about", true))
                .addSubcommands(new SubcommandData("all", "Administrator: View all data in an JSON file")
                        .addOption(OptionType.STRING, "table", "Table to view data from", true))
                .setDefaultPermissions(DefaultMemberPermissions.DISABLED));
        commands.add(Commands.slash("ezkick", "Administrator: Notify, Logs, and kicks a user")
                .addOption(OptionType.USER, "user", "User to kick", true)
                .addOption(OptionType.ATTACHMENT, "evidenceAsImage", "Evidence: as attachment", false)
                .addOption(OptionType.STRING, "evidenceAsText", "Evidence: as text", false)
                .setDefaultPermissions(DefaultMemberPermissions.DISABLED));
        if (event.getGuild().getId().equals(Bot.getBot().getDeployment().get("guild")) || event.getGuild().getId().equals(Bot.getBot().getAnalysis().get("guild"))) {
            event.getGuild().updateCommands().addCommands(commands).queue();
        }
    }
}