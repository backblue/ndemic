package org.backblue.commands;

import com.sun.management.OperatingSystemMXBean;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Core;
import org.backblue.utilities.FormatTime;
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
            String whatsNew = "General bug fixes and improvements";
            try {
                Files.readString(Path.of("data/news.txt"));
            } catch (IOException ignored) {}

            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(Color.BLUE)
                    .setTitle("About Bot: v" + Core.VERSION)
                    .addField("CPU", "`" + Math.round(OS.getCpuLoad()*100) + "%`", true)
                    .addField("Memory", "`" + used / 1000000 + " MB / " + Runtime.getRuntime().totalMemory() / 1000000 + " MB`", true)
                    .addField("Uptime", "`" + FormatTime.formatTimeShort(Instant.now().getEpochSecond() - Core.BOOT) + "`", true)
                    .addField("What's new?", whatsNew, false)
                    .setFooter("Built by killergotrekt for Ndemic");

            event.replyEmbeds(embed.build()).queue();
        }
    }

}
