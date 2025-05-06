package org.backblue.events;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.automod.AutoModExecutionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Core;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static net.dv8tion.jda.api.requests.ErrorResponse.CANNOT_SEND_TO_USER;

public class AutoModAlert extends ListenerAdapter {
    private static long UNIX_TIMESTAMP_MOD = Instant.now().getEpochSecond();
    private static final long COOLDOWN = 1; // Fixed Cooldown for pings
    @Override
    public void onAutoModExecution(@NotNull AutoModExecutionEvent event) {

        if (Core.MODULES.get("autoModNotify")) {
            TextChannel channel = event.getJDA().getTextChannelById(Core.DEPLOYMENT.get("channel.cmd"));
            Role pingRole = event.getJDA().getRoleById(Core.DEPLOYMENT.get("role.mod"));
            if (UNIX_TIMESTAMP_MOD + COOLDOWN < Instant.now().getEpochSecond()) {
                channel.sendMessage(pingRole.getAsMention()).queue();
                UNIX_TIMESTAMP_MOD = Instant.now().getEpochSecond();
            }
            TextChannel analysis = event.getJDA().getTextChannelById(Core.ANALYTICS.get("autoMod"));
            if (analysis != null) {
                analysis.sendMessage("AutoMod @ Mods pinged for violation: " + "https://discord.com/channels/" + event.getGuild().getId() + "/" + Core.DEPLOYMENT.get("channel.cmd") + "/" + event.getAlertMessageId()).queue();
            }
        }
    }
}
