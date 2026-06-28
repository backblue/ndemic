package org.backblue.core;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.backblue.utilities.FeatureFlag;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class Deployment extends ListenerAdapter {

    private final JSONObject settings;

    @Override
    public void onGuildReady(@NonNull GuildReadyEvent event) {
        List<CommandData> commands = new ArrayList<>();

        commands.add(Commands.slash("ping", "Ping, pong!"));
        commands.add(Commands.slash("about", "Uptime & bot information"));
        commands.add(Commands.slash("badge", "Select a role icon that appears next to your username"));

        OptionData featuresList = new OptionData(OptionType.STRING, "flag", "The selected feature", true);
        for (FeatureFlag feature : FeatureFlag.values()) featuresList.addChoice(feature.toString(), String.valueOf(feature.ordinal()));

        commands.add(Commands.slash("features", "Feature flag management")
                .addSubcommands(new SubcommandData("list", "List status of feature flags/modules"))
                .addSubcommands(new SubcommandData("enable", "Temporarily enable a feature flag/module").addOptions(featuresList))
                .addSubcommands(new SubcommandData("disable", "Temporarily disable a feature flag/module").addOptions(featuresList))
                .setDefaultPermissions(DefaultMemberPermissions.DISABLED));
        commands.add(Commands.slash("scan", "Scan management")
                .addSubcommands(new SubcommandData("profile", "Manually initiate a profile scan for an user")
                        .addOption(OptionType.USER, "user", "Select a user", true))
                .addSubcommands(new SubcommandData("link", "Manually initiate a link scan")
                        .addOption(OptionType.STRING, "link", "Input valid link", true))
                .setDefaultPermissions(DefaultMemberPermissions.DISABLED));
        commands.add(Commands.slash("ezpunish", "Remove an user with the 3 steps: kick/ban, notify and log")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS)));
        commands.add(Commands.message("EZPunish...")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS)));
        if (settings.optBoolean("$enablePrivacyPolicyCommand")) commands.add(Commands.slash("privacy", "View the bot's privacy policy"));

        if (event.getGuild().getId().equals(settings.optString("_deploy", "")) || event.getGuild().getId().equals(settings.optString("_debug", ""))) {
            event.getGuild().updateCommands().addCommands(commands).queue();
        }
    }

    public Deployment(JSONObject settings) {
        this.settings = settings;
    }
}
