package org.backblue.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.backblue.core.Bot;
import org.backblue.enums.SetChannel;
import org.backblue.extension.Deployable;
import org.backblue.enums.Feature;
import org.backblue.extension.SelfMutable;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

public class Features extends ListenerAdapter implements Deployable, SelfMutable {

    Bot bot;

    public Features(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("features") && event.getSubcommandName() != null) {
            if (event.getSubcommandName().equals("list")) {
                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("Feature Flags");
                embed.addField("Enabled - " + bot.getFeatures().size(), bot.getFeatures().toString(), false);
                embed.addField("Disabled - " + EnumSet.complementOf(bot.getFeatures()).size(), EnumSet.complementOf(bot.getFeatures()).toString(), false);
                event.replyEmbeds(embed.build()).setEphemeral(true).queue();
                return;
            }
            String feature = Objects.requireNonNull(event.getOption("flag")).getAsString();
            if (event.getSubcommandName().equals("enable")) {
                bot.getFeature(feature);
                if (Objects.requireNonNull(bot.getFeature(feature)).restricted()) {
                    event.reply("This feature can only be re-enabled with a restart.").setEphemeral(true).queue();
                    return;
                }
                bot.enableFeature(bot.getFeature(feature));
            } else {
                bot.disableFeature(bot.getFeature(feature));
            }
            bot.getIO().send(SetChannel.DebugEnforcement, event.getUser().getName() + " changed setting " + Objects.requireNonNull(bot.getFeature(feature)) + " to " + (event.getSubcommandName().equals("enable") ? "**enabled**." : "**disabled**."));
            event.reply(":white_check_mark: Feature **" + Objects.requireNonNull(bot.getFeature(feature)) + "** is " + (event.getSubcommandName().equals("enable") ? "**enabled**." : "**disabled**.")).setEphemeral(true).queue();
        }
    }

    @Override
    public List<CommandData> cmds() {
        OptionData featuresList = new OptionData(OptionType.STRING, "flag", "The selected feature", true);
        for (Feature feature : Feature.values()) featuresList.addChoice(feature.toString(), String.valueOf(feature.ordinal()));

        return List.of(Commands.slash("features", "Feature flag management")
                .addSubcommands(new SubcommandData("list", "List status of feature flags/modules"))
                .addSubcommands(new SubcommandData("enable", "Enable a feature flag/module").addOptions(featuresList))
                .addSubcommands(new SubcommandData("disable", "Disable a feature flag/module").addOptions(featuresList))
                .setDefaultPermissions(DefaultMemberPermissions.DISABLED));
    }
}
