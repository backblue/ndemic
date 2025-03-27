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
            analysis.sendMessage("AutoMod @ Mods pinged for violation: " + "https://discord.com/channels/" + event.getGuild().getId() + "/" + Core.DEPLOYMENT.get("channel.cmd") + "/" + event.getAlertMessageId()).queue();
        }

        if (Core.MODULES.get("autoModTakeExtremeAction")) {
            for (int i = 0; i < Integer.getInteger(Core.DEPLOYMENT.get("kick.length")); i++) {
                if (event.getRuleId().equals(Core.DEPLOYMENT.get("kick." + i))) {
                    if (event.getAlertMessageId() != null) {
                        User user = event.getJDA().getUserById(event.getUserId());
                        TextChannel logChannel = event.getJDA().getTextChannelById(Core.DEPLOYMENT.get("channel.warn"));

                        MessageEmbed message = new EmbedBuilder()
                                .setColor(Color.ORANGE)
                                .setTitle("Removed from " + event.getGuild().getName())
                                .addField("Offending Message:", event.getContent(), false)
                                .addField("Re-read the rules before rejoining.", Core.SERVER_RULES, false)
                                .addField("You can re-join the server.", "discord.gg/ndemic", false)
                                .setFooter("Contact Discord Mods/Ndemic Community Manager for any questions")
                                .build();

                        logChannel.sendMessage(user.getAsMention() + " - Kick - AutoMod Infraction" + "\n" + "https://discord.com/channels/" + event.getGuild().getId() + "/" + Core.DEPLOYMENT.get("channel.cmd") + "/" + event.getAlertMessageId()).queue();
                        user.openPrivateChannel()
                                .flatMap(channel -> channel.sendMessageEmbeds(message))
                                .onErrorFlatMap(CANNOT_SEND_TO_USER::test,
                                        (error) -> logChannel.sendMessage("Attempted to send message to " + user.getAsMention() + ", but their DMs were closed."))
                                .queue();
                        event.getGuild().kick(user).reason("Automated action from AutoMod Rule trigger.").queue();
                        TextChannel analysis = event.getJDA().getTextChannelById(Core.ANALYTICS.get("autoMod"));
                        analysis.sendMessage("AutoMod Kick alert: " + "https://discord.com/channels/" + event.getGuild().getId() + "/" + Core.DEPLOYMENT.get("channel.cmd") + "/" + event.getAlertMessageId()).queue();
                        break;
                    }
                }
            }
            for (int j = 0; j < Integer.getInteger(Core.DEPLOYMENT.get("ban.length")); j++) {
                if (event.getRuleId().equals(Core.DEPLOYMENT.get("ban." + j))) {
                    if (event.getAlertMessageId() != null) {
                        User user = event.getJDA().getUserById(event.getUserId());
                        TextChannel logChannel = event.getJDA().getTextChannelById(Core.DEPLOYMENT.get("channel.warn"));

                        MessageEmbed message = new EmbedBuilder()
                                .setColor(Color.RED)
                                .setTitle("Banned from " + event.getGuild().getName())
                                .addField("Offending Message:", event.getContent(), false)
                                .addField("Guild Rules", Core.SERVER_RULES, false)
                                .setFooter("You cannot re-join the server.")
                                .build();

                        logChannel.sendMessage(user.getAsMention() + " - Ban - AutoMod Infraction" + "\n" + "https://discord.com/channels/" + event.getGuild().getId() + "/" + Core.DEPLOYMENT.get("channel.cmd") + "/" + event.getAlertMessageId()).queue();
                        user.openPrivateChannel()
                                .flatMap(channel -> channel.sendMessageEmbeds(message))
                                .onErrorFlatMap(CANNOT_SEND_TO_USER::test,
                                        (error) -> logChannel.sendMessage("Attempted to send message to " + user.getAsMention() + ", but their DMs were closed."))
                                .queue();

                        event.getGuild().ban(user, 0, TimeUnit.DAYS).reason("Automated action from AutoMod Rule trigger.").queue();
                        TextChannel analysis = event.getJDA().getTextChannelById(Core.ANALYTICS.get("autoMod"));
                        analysis.sendMessage("AutoMod Ban alert: " + "https://discord.com/channels/" + event.getGuild().getId() + "/" + Core.DEPLOYMENT.get("channel.cmd") + "/" + event.getAlertMessageId()).queue();
                        break;
                    }
                }
            }
        }
    }
}
