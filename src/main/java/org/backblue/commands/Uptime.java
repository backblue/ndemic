package org.backblue.commands;

import com.sun.management.OperatingSystemMXBean;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Core;
import org.backblue.libraries.FormatTime;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.lang.management.ManagementFactory;
import java.time.Instant;

public class Uptime extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {

        OperatingSystemMXBean OS = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        if (event.getName().equals("uptime")) {
            long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(Color.BLUE)
                    .setTitle("About Bot: v" + Core.VERSION)
                    .addField("CPU", "`" + Math.round(OS.getCpuLoad()*100) + "%`", true)
                    .addField("Memory", "`" + used / 1000000 + " MB / " + Runtime.getRuntime().totalMemory() / 1000000 + " MB`", true)
                    .addField("Uptime", "`" + FormatTime.formatTimeShort(Instant.now().getEpochSecond() - Core.BOOT) + "`", true)
                    .setFooter("Built by killergotrekt for Ndemic");

            event.replyEmbeds(embed.build()).queue();
        }
    }

}
