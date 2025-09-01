package org.backblue.events;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.automod.AutoModExecutionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.backblue.Bot;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class AutoModAlert extends ListenerAdapter {

    private static ArrayList<String> RULE_IDS;

    private static String COMPROMISED_ACCOUNT_RULE_ID;
    private static String SLURS_RULE_ID;
    private static String INVITE_LINK;

    public static void generateStaticVariables() {
        COMPROMISED_ACCOUNT_RULE_ID = Bot.getBot().getDeployment().get("autoModDefaultActionsIds.compromisedAccount");
        SLURS_RULE_ID = Bot.getBot().getDeployment().get("autoModDefaultActionsIds.slurs");
        INVITE_LINK = Bot.getBot().getDeployment().get("autoModDefaultActionsIds.inviteLink");
        RULE_IDS = new ArrayList<>(Arrays.asList(COMPROMISED_ACCOUNT_RULE_ID, SLURS_RULE_ID));
    }

    @Override
    public void onAutoModExecution(@NotNull AutoModExecutionEvent event) {
        if (Bot.getBot().getModuleValue("automaticActions") && RULE_IDS.contains(event.getRuleId())) {
            if (event.getRuleId().equals(COMPROMISED_ACCOUNT_RULE_ID)) {
                User user = event.getJDA().getUserById(event.getUserIdLong());
                TextChannel warnChannel = event.getJDA().getTextChannelById(Bot.getBot().getDeployment().get("channels.warn"));
                if (user != null && event.getChannel() != null && warnChannel != null) {
                    MessageEmbed embed = new EmbedBuilder()
                            .setColor(Color.ORANGE)
                            .setTitle("Removed from " + event.getGuild().getName())
                            .setDescription("Your account may have been compromised, and you have been removed from this server to help protect your account and the server.")
                            .addField("Offending Message", event.getContent(), false)
                            .addField("Re-join invite Link", INVITE_LINK, false)
                            .build();
                    Bot.getBot().sendUserMessage(user, embed);
                    event.getGuild().ban(user, 1, TimeUnit.HOURS).reason("Automated Action: Compromised Account, Removed 1 hour worth of messages.").queue();
                    Bot.getBot().sendDebugMessage("autoMod", "AutoMod Kick alert: " + "https://discord.com/channels/" + event.getGuild().getId() + "/" + event.getChannel().getId() + "/" + event.getAlertMessageId());
                    warnChannel.sendMessage(user.getAsMention() + " - Kick - Potentially Compromised Account\n" + "https://discord.com/channels/" + event.getGuild().getId() + "/" + event.getChannel().getId() + "/" + event.getAlertMessageId()).queue();
                    event.getGuild().unban(user).queue();
                }
            }
            if (event.getRuleId().equals(SLURS_RULE_ID)) {
                User user = event.getJDA().getUserById(event.getUserIdLong());
                TextChannel warnChannel = event.getJDA().getTextChannelById(Bot.getBot().getDeployment().get("channels.warn"));
                if (user != null && event.getChannel() != null && warnChannel != null) {
                    MessageEmbed embed = new EmbedBuilder()
                            .setColor(Color.RED)
                            .setTitle("Banned from " + event.getGuild().getName())
                            .setDescription("You cannot re-join the server.")
                            .addField("Offending Message", event.getContent(), false)
                            .build();
                    Bot.getBot().sendUserMessage(user, embed);
                    event.getGuild().ban(user, 1, TimeUnit.DAYS).reason("Automated Action: Extreme Slurs, Removed 1 day worth of messages.").queue();
                    Bot.getBot().sendDebugMessage("autoMod", "AutoMod Ban alert: " + "https://discord.com/channels/" + event.getGuild().getId() + "/" + event.getChannel().getId() + "/" + event.getAlertMessageId());
                    warnChannel.sendMessage(user.getAsMention() + " - Ban - Slurs\n" + "https://discord.com/channels/" + event.getGuild().getId() + "/" + event.getChannel().getId() + "/" + event.getAlertMessageId()).queue();
                }
            }
            return;
        }
        if (Bot.getBot().getModuleValue("discordAutoModNotify")) {
            if (event.getChannel() == null || event.getAlertMessageId() == null) {
                return;
            }
            TextChannel channel = event.getJDA().getTextChannelById(Bot.getBot().getDeployment().get("channels.cmd"));
            Role role = event.getJDA().getRoleById(Bot.getBot().getDeployment().get("roles.optIn"));
            if (channel != null && role != null) {
                channel.sendMessage(role.getAsMention()).queue();
                Bot.getBot().sendDebugMessage("autoMod", "AutoMod @ Mods pinged for violation: " + "https://discord.com/channels/" + event.getGuild().getId() + "/" + channel.getId() + "/" + event.getAlertMessageId());
            }
        }
    }
}
