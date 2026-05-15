package org.backblue.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.core.Bot;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class Features extends ListenerAdapter {

    Bot bot;

    public Features(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("features") && event.getSubcommandName() != null) {
            double feature = Objects.requireNonNull(event.getOption("featuresList")).getAsDouble();
            if (event.getSubcommandName().equals("enable")) {
                bot.enableFeature(bot.getFeature(feature));
            } else {
                bot.disableFeature(bot.getFeature(feature));
            }

            event.reply(":white_check_mark: Feature **" + Objects.requireNonNull(bot.getFeature(feature)) + "** is " + (event.getSubcommandName().equals("enable") ? "**enabled**." : "**disabled**.")).queue();
        }
    }

}
