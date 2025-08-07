package org.backblue.commands;

import com.sun.management.OperatingSystemMXBean;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Bot;
import org.backblue.utilities.TimeFormat;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public class Uptime extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {

        OperatingSystemMXBean OS = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        if (event.getName().equals("uptime")) {
            long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            String whatsNew = null;
            try {
                whatsNew = Files.readString(Path.of("data/news.txt"));
            } catch (IOException ignored) {}

            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(Color.BLUE)
                    .setTitle(event.getJDA().getSelfUser().getName() + ": v" + Bot.VERSION)
                    .addField("CPU", "`" + Math.round(OS.getCpuLoad()*100) + "%`", true)
                    .addField("Memory", "`" + used / 1000000 + " MB / " + Runtime.getRuntime().totalMemory() / 1000000 + " MB`", true)
                    .addField("Uptime", "`" + TimeFormat.formatTimeShort(Instant.now().getEpochSecond() - Bot.BOOT) + "`", true)
                    .setFooter("Built by killergotrekt for Ndemic");
            if (whatsNew != null && !whatsNew.isBlank()) {
                embed.addField("What's New", whatsNew, false);
            }
            if (Bot.getBot().getSettings().getString("watermark") != null && Bot.getBot().getSettings().getString("watermarkLogoLink") != null) {
                embed.setFooter(Bot.getBot().getSettings().getString("watermark"), Bot.getBot().getSettings().getString("watermarkLogoLink"));
            }
            event.replyEmbeds(embed.build()).queue();
        }
    }

}