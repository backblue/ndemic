package org.backblue.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Core;
import org.backblue.libraries.FormatTime;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

public class Uptime extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("uptime")) {
            long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            event.reply("Version: **" + Core.SETTINGS.getString("version") + "**\nUptime: `" + FormatTime.formatTimeShort(Instant.now().getEpochSecond() - Core.BOOT) + "`\n" +
                    "Memory: `" + used / 1000000 + " MB` / `" + Runtime.getRuntime().totalMemory() / 1000000 + " MB`").queue();
        }
    }

}
