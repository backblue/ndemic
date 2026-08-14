package org.backblue.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.core.Bot;
import org.backblue.enums.DefinedChannel;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Objects;

public class Features extends ListenerAdapter {

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
            bot.getIO().send(DefinedChannel.DebugEnforcement, event.getUser().getName() + " changed setting " + Objects.requireNonNull(bot.getFeature(feature)) + " to " + (event.getSubcommandName().equals("enable") ? "**enabled**." : "**disabled**."));
            event.reply(":white_check_mark: Feature **" + Objects.requireNonNull(bot.getFeature(feature)) + "** is " + (event.getSubcommandName().equals("enable") ? "**enabled**." : "**disabled**.")).queue();
        }
    }

}
