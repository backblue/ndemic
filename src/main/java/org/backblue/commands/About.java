package org.backblue.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.core.Bot;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.lang.management.ManagementFactory;

public class About extends ListenerAdapter {

    Bot bot;
    String watermark;

    public About(Bot bot, String watermark) {
        this.bot = bot;
        this.watermark = watermark;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("about")) {
            long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(Color.CYAN)
                    .setTitle(event.getJDA().getSelfUser().getName() + ": v" + bot.major + "." + bot.minor + "." + bot.patch)
                    .addField("Memory", "`" + used / 1000000 + " MB / " + Runtime.getRuntime().totalMemory() / 1000000 + " MB`", true)
                    .addField("Uptime", Bot.formatTimeShort(ManagementFactory.getRuntimeMXBean().getUptime()), true);
            if (!watermark.isEmpty()) {
                embed.setFooter(this.watermark);
            }
            event.replyEmbeds(embed.build()).queue();
        }
    }

}
