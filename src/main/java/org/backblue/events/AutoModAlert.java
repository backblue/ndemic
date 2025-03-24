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
    private static final long COOLDOWN = 2; // Fixed Cooldown for pings
    @Override
    public void onAutoModExecution(@NotNull AutoModExecutionEvent event) {

        if (!Core.MODULES.get("autoModNotify")) {
            return;
        }

        if (Core.MODULES.get("autoModTakeExtremeAction")) {

            if (event.getRuleId().equals(Core.SETTINGS.getString("autoModChannel_kickID"))) {
                if (event.getAlertMessageId() != null) {
                    User user = event.getJDA().getUserById(event.getUserId());
                    TextChannel logChannel = event.getJDA().getTextChannelById(Core.SETTINGS.getString("ndemicWarnChannel"));

                    MessageEmbed message = new EmbedBuilder()
                            .setColor(Color.ORANGE)
                            .setTitle("Removed from " + event.getGuild().getName())
                            .addField("Offending Message:", event.getContent(), false)
                            .addField("Re-read the rules before rejoining.", Core.SERVER_RULES, false)
                            .addField("You can re-join the server.", "discord.gg/ndemic", false)
                            .setFooter("Contact Discord Mods/Ndemic Community Manager for any questions")
                            .build();

                    logChannel.sendMessage(user.getAsMention() + " - Kick - AutoMod Infraction" + "\n" + "https://discord.com/channels/" + event.getGuild().getId() + "/" + Core.SETTINGS.getString("ndemicBotChannel") + "/" + event.getAlertMessageId()).queue();
                    user.openPrivateChannel()
                            .flatMap(channel -> channel.sendMessageEmbeds(message))
                            .onErrorFlatMap(CANNOT_SEND_TO_USER::test,
                                    (error) -> logChannel.sendMessage("Attempted to send message to " + user.getAsMention() + ", but their DMs were closed."))
                            .queue();
                    event.getGuild().kick(user).reason("Automated action from AutoMod Rule trigger.").queue();
                }
                return;
            } else if (event.getRuleId().equals(Core.SETTINGS.getString("autoModChannel_banID"))) {
                User user = event.getJDA().getUserById(event.getUserId());
                Member member = event.getJDA().getGuildById(Core.SETTINGS.getString("ndemicGuild")).getMember(user);
                TextChannel logChannel = event.getJDA().getTextChannelById(Core.SETTINGS.getString("ndemicWarnChannel"));

                MessageEmbed message = new EmbedBuilder()
                        .setColor(Color.ORANGE)
                        .setTitle("Banned from " + event.getGuild().getName())
                        .setDescription("You can not re-join this server.")
                        .addField("Offending Message:", event.getContent(), false)
                        .addField("This is the rules for your reference:", Core.SERVER_RULES, false)
                        .setFooter("This ban is permanent")
                        .build();

                logChannel.sendMessage(user.getAsMention() + " - Kick - AutoMod Infraction" + "\n" + "https://discord.com/channels/" + event.getGuild().getId() + "/" + Core.SETTINGS.getString("ndemicBotChannel") + "/" + event.getAlertMessageId()).queue();
                user.openPrivateChannel()
                        .flatMap(channel -> channel.sendMessageEmbeds(message))
                        .onErrorFlatMap(CANNOT_SEND_TO_USER::test,
                                (error) -> logChannel.sendMessage("Attempted to send message to " + user.getAsMention() + ", but their DMs were closed."))
                        .queue();

                event.getGuild().ban(UserSnowflake.fromId(event.getUserId()), 0, TimeUnit.DAYS).queue();
                return;
            }
        }

        TextChannel channel = event.getJDA().getTextChannelById(Core.SETTINGS.getString("ndemicBotChannel"));
        Role pingRole = event.getJDA().getRoleById(Core.SETTINGS.getString("ndemicModerators"));
        if (UNIX_TIMESTAMP_MOD + COOLDOWN < Instant.now().getEpochSecond()) {
            channel.sendMessage(pingRole.getAsMention()).queue();
            UNIX_TIMESTAMP_MOD = Instant.now().getEpochSecond();
        }
    }
}
